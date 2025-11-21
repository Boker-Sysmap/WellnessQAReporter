package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.core.excel.builder.ReportWorkbookBuilder;
import com.sysmap.wellness.report.service.DefectAnalyticalService;
import com.sysmap.wellness.report.service.FunctionalSummaryService;
import com.sysmap.wellness.report.service.engine.KPIEngine;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.IdentifierParser;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class ReportGenerator {

    public void generateReport(
        Map<String, JSONObject> consolidatedData,
        Path outputPath
    ) {
        LoggerUtils.section("📘 GERAÇÃO DE RELATÓRIO (PREMIUM)");

        long start = System.nanoTime();

        try {
            // -----------------------------------------------------
            // Caminho final
            // -----------------------------------------------------
            Path finalPath = prepareOutputPath(outputPath);

            FunctionalSummaryService summaryService = new FunctionalSummaryService();
            DefectAnalyticalService defectService = new DefectAnalyticalService();

            // -----------------------------------------------------
            // KPIEngine
            // -----------------------------------------------------
            LoggerUtils.section("📊 KPIs via KPIEngine");

            KPIEngine kpiEngine = new KPIEngine(
                IdentifierParser::parse,
                null // Coverage + Results já são incluídos automaticamente
            );

            // Agora populate:
            // - kpisByProject
            // - consolidatedData[x].releaseSummaries
            Map<String, List<KPIData>> kpisByProject =
                kpiEngine.calculateForAllProjects(consolidatedData);

            LoggerUtils.success("✔ KPIs calculados");

            // -----------------------------------------------------
            // Resumo de releases — a partir do dado já inserido pelo KPIEngine
            // -----------------------------------------------------
            Map<String, Map<String, JSONObject>> summariesByProject = new LinkedHashMap<>();

            for (String project : consolidatedData.keySet()) {

                JSONObject obj = consolidatedData.get(project);
                JSONArray arr = obj.optJSONArray("releaseSummaries");

                Map<String, JSONObject> map = new LinkedHashMap<>();

                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.getJSONObject(i);
                        String rel = row.optString("releaseId", "");
                        if (!rel.isBlank()) {
                            map.put(rel, row);
                        }
                    }
                }

                summariesByProject.put(project, map);
            }

            // -----------------------------------------------------
            // Mapa PROJETO → SET<RELEASE>
            // -----------------------------------------------------
            Map<String, Set<String>> releasesByProject = new LinkedHashMap<>();

            for (String project : summariesByProject.keySet()) {
                releasesByProject.put(project, summariesByProject.get(project).keySet());
            }

            // -----------------------------------------------------
            // Resumo Funcional
            // -----------------------------------------------------
            Map<String, JSONObject> functionalSummaries =
                summaryService.prepareData(consolidatedData);

            // -----------------------------------------------------
            // Defeitos Analítico
            // -----------------------------------------------------
            Map<String, JSONArray> enrichedDefects =
                defectService.prepareData(consolidatedData);

            // -----------------------------------------------------
            // Criar Excel
            // -----------------------------------------------------
            ReportWorkbookBuilder builder = new ReportWorkbookBuilder();
            XSSFWorkbook wb = builder.buildWorkbook(
                consolidatedData,
                kpisByProject,
                releasesByProject,
                functionalSummaries,
                enrichedDefects
            );

            saveWorkbook(wb, finalPath);

            // -----------------------------------------------------
            // Histórico RUN-BASED
            // -----------------------------------------------------
            generateRunBasedHistory(consolidatedData, finalPath, releasesByProject);

            long end = System.nanoTime();

            LoggerUtils.success("🏁 Relatório gerado: " + finalPath);
            MetricsCollector.timing("report.totalMs", (end - start) / 1_000_000);

        } catch (Exception e) {
            LoggerUtils.error("💥 Erro crítico no ReportGenerator", e);
            MetricsCollector.increment("reportErrors");
        }
    }

    // =====================================================================
    // RELEASE MAP (NÃO USADO MAIS — substituído por summaries)
    // =====================================================================

    @Deprecated
    private Map<String, Set<String>> buildReleaseMap(
        Map<String, List<KPIData>> kpisByProject
    ) {
        Map<String, Set<String>> map = new LinkedHashMap<>();

        for (String project : kpisByProject.keySet()) {

            Set<String> releases = new TreeSet<>(Comparator.reverseOrder());

            for (KPIData k : kpisByProject.get(project)) {
                if (k.getGroup() != null && !k.getGroup().isBlank()) {
                    releases.add(k.getGroup());
                }
            }

            map.put(project, releases);
        }

        return map;
    }

    // =====================================================================
    // Output / History (inalterado)
    // =====================================================================

    private Path prepareOutputPath(Path outputPath) throws IOException {
        Path dir = Path.of("output", "reports");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        return dir.resolve(outputPath.getFileName());
    }

    private void saveWorkbook(XSSFWorkbook wb, Path finalPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
            wb.write(fos);
        }
        LoggerUtils.success("💾 Excel salvo em " + finalPath);
    }

    private void generateRunBasedHistory(
        Map<String, JSONObject> consolidated,
        Path finalPath,
        Map<String, Set<String>> releasesByProject
    ) {

        LoggerUtils.section("📚 Salvando histórico RUN-BASED");

        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());

        for (String project : consolidated.keySet()) {

            Set<String> releases = releasesByProject.get(project);
            if (releases == null || releases.isEmpty()) continue;

            for (String releaseId : releases) {

                Path relDir = Paths.get("historico", "releases",
                    normalize(project), year, releaseId);
                Path snapDir = Paths.get("historico", "snapshots",
                    normalize(project), year, releaseId);

                try {
                    Files.createDirectories(relDir);
                    Files.createDirectories(snapDir);

                    JSONObject info = new JSONObject()
                        .put("project", project)
                        .put("releaseId", releaseId)
                        .put("year", year)
                        .put("generatedAt", now.toString())
                        .put("reportFile", finalPath.getFileName().toString());

                    JSONObject filtered =
                        filterConsolidatedByRelease(consolidated.get(project), releaseId);

                    writeJson(filtered, snapDir.resolve("consolidated.json"));
                    writeJson(info, relDir.resolve("release_snapshot.json"));

                } catch (Exception e) {
                    LoggerUtils.error(
                        "⚠ Falha ao salvar histórico para " + project + "/" + releaseId, e
                    );
                }
            }
        }
    }

    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        if (full == null) return null;

        JSONObject filtered = new JSONObject(full.toString());

        JSONArray plans = full.optJSONArray("plan");
        JSONArray runs = full.optJSONArray("run");

        JSONArray fp = new JSONArray();
        JSONArray fr = new JSONArray();

        if (plans != null) {
            for (int i = 0; i < plans.length(); i++) {
                JSONObject p = plans.optJSONObject(i);
                if (p == null) continue;

                var parsed = IdentifierParser.parse(p.optString("title", ""));
                if (parsed != null && releaseId.equals(parsed.getOfficialId())) {
                    fp.put(p);
                }
            }
        }

        if (runs != null) {
            for (int i = 0; i < runs.length(); i++) {
                JSONObject r = runs.optJSONObject(i);
                if (r == null) continue;

                var parsed = IdentifierParser.parse(r.optString("title", ""));
                if (parsed != null && releaseId.equals(parsed.getOfficialId())) {
                    fr.put(r);
                }
            }
        }

        filtered.put("plan", fp);
        filtered.put("run", fr);

        return filtered;
    }

    private void writeJson(JSONObject json, Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(json.toString(2));
        } catch (Exception e) {
            LoggerUtils.error("❌ Erro ao salvar JSON em " + path, e);
        }
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
            .replace(" ", "_")
            .replaceAll("[^a-z0-9_]", "");
    }
}
