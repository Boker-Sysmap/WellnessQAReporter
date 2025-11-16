package com.sysmap.wellness.report;

import com.sysmap.wellness.report.service.*;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.sheet.*;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ReportGenerator (PREMIUM + RUN-BASED + KPIEngine)
 *
 * Esta versão consolida:
 *  ✔ Painel Consolidado com múltiplas releases
 *  ✔ KPIs vindos da engine de KPIs + histórico
 *  ✔ Releases reais por projeto
 *  ✔ Snapshots RUN-BASED por release
 *  ✔ Estrutura limpa e compatível com extensões futuras
 */
public class ReportGenerator {

    public void generateReport(
        Map<String, JSONObject> consolidatedData,
        Path outputPath
    ) {
        LoggerUtils.section("📘 GERAÇÃO DE RELATÓRIO (PREMIUM)");

        long start = System.nanoTime();

        try {
            // -----------------------------------------------------
            // 1) Preparar caminho final do relatório
            // -----------------------------------------------------
            Path finalPath = prepareOutputPath(outputPath);

            String fileBasedReleaseId =
                stripExt(finalPath.getFileName().toString());

            LoggerUtils.info("🔖 ReleaseId (fallback) via nome do arquivo: " + fileBasedReleaseId);

            // Serviços auxiliares
            FunctionalSummaryService summaryService =
                new FunctionalSummaryService();
            DefectAnalyticalService defectService =
                new DefectAnalyticalService();

            // -----------------------------------------------------
            // 2) KPIs via KPIEngine (inclui histórico)
            // -----------------------------------------------------
            LoggerUtils.section("📊 KPIs via KPIEngine");

            KPIEngine kpiEngine = new KPIEngine();

            Map<String, List<KPIData>> kpisByProject =
                kpiEngine.calculateForAllProjects(consolidatedData, fileBasedReleaseId);

            LoggerUtils.success("✔ KPIs calculados com histórico gravado");

            // release "principal" por projeto (usada apenas nas abas executivas)
            Map<String, String> releaseByProject =
                buildReleaseByProjectMap(kpisByProject, fileBasedReleaseId);

            // -----------------------------------------------------
            // 3) Resumo Funcional
            // -----------------------------------------------------
            LoggerUtils.section("📘 Resumo Funcional");

            Map<String, JSONObject> functionalSummaries =
                summaryService.prepareData(consolidatedData);

            // -----------------------------------------------------
            // 4) Defeitos Analítico (enriquecido)
            // -----------------------------------------------------
            LoggerUtils.section("🐞 Defeitos (RUN-BASED)");

            Map<String, JSONArray> enrichedDefects =
                defectService.prepareData(consolidatedData);

            // -----------------------------------------------------
            // 5) Gerar Excel completo
            // -----------------------------------------------------
            try (XSSFWorkbook wb = new XSSFWorkbook()) {

                // Painel Consolidado
                ExecutiveConsolidatedSheet.create(
                    wb,
                    kpisByProject,
                    releaseByProject
                );
                wb.setSheetOrder("Painel Consolidado", 0);

                // Resumos Executivos (1 por projeto)
                for (String project : kpisByProject.keySet()) {

                    String releaseId = releaseByProject.get(project);

                    ExecutiveKPISheet.create(
                        wb,
                        kpisByProject.get(project),
                        project + " – Resumo Executivo",
                        releaseId
                    );
                }

                // Resumo Funcional
                for (String project : functionalSummaries.keySet()) {

                    JSONObject summary = functionalSummaries.get(project);
                    Map<String, JSONObject> map = new LinkedHashMap<>();
                    map.put(project, summary);

                    new FunctionalSummarySheet().create(
                        wb,
                        map,
                        project + " – Resumo Funcional"
                    );
                }

                // Defeitos Analítico
                for (String project : enrichedDefects.keySet()) {

                    Map<String, JSONArray> map = new LinkedHashMap<>();
                    map.put(project, enrichedDefects.get(project));

                    new DefectAnalyticalReportSheet().create(
                        wb,
                        map,
                        project + " – Defeitos Analítico"
                    );
                }

                // Dashboard
                for (String project : enrichedDefects.keySet()) {

                    JSONObject d = new JSONObject();
                    d.put("defects", enrichedDefects.get(project));

                    DefectsDashboardSheet.create(
                        wb,
                        d,
                        project + " – Defeitos Dashboard"
                    );
                }

                // Sintético
                for (String project : enrichedDefects.keySet()) {

                    JSONObject d = new JSONObject();
                    d.put("defects", enrichedDefects.get(project));

                    DefectsSyntheticSheet.create(
                        wb,
                        d,
                        project + " – Defeitos Sintético"
                    );
                }

                adjustAllColumns(wb);
                saveWorkbook(wb, finalPath);

                // -----------------------------------------------------
                // 6) Histórico RUN-BASED (agora multi-release)
                // -----------------------------------------------------
                generateRunBasedHistory(
                    consolidatedData,
                    enrichedDefects,
                    functionalSummaries,
                    finalPath,
                    kpisByProject
                );
            }

            long end = System.nanoTime();
            LoggerUtils.success("🏁 Relatório gerado: " + finalPath);

            MetricsCollector.timing(
                "report.totalMs",
                (end - start) / 1_000_000
            );

        } catch (Exception e) {
            LoggerUtils.error("💥 Erro crítico no ReportGenerator", e);
            MetricsCollector.increment("reportErrors");
        }
    }

    // =====================================================================================
    // 🔧 Helpers
    // =====================================================================================

    private Path prepareOutputPath(Path outputPath) throws IOException {

        Path dir = Path.of("output", "reports");
        if (!Files.exists(dir)) Files.createDirectories(dir);

        Path finalPath = dir.resolve(outputPath.getFileName());

        LoggerUtils.step("📄 Arquivo final: " + finalPath);
        return finalPath;
    }

    private void adjustAllColumns(Workbook wb) {

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {

            Sheet sheet = wb.getSheetAt(i);
            if (sheet.getRow(0) == null) continue;

            int cols = sheet.getRow(0).getPhysicalNumberOfCells();

            for (int c = 0; c < cols; c++) {
                try {
                    sheet.autoSizeColumn(c);
                    sheet.setColumnWidth(
                        c,
                        Math.min(sheet.getColumnWidth(c) + 1500, 18000)
                    );
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveWorkbook(Workbook wb, Path finalPath) throws IOException {

        try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
            wb.write(fos);
        }

        LoggerUtils.success("💾 Excel salvo em " + finalPath);
    }

    private String stripExt(String name) {
        int idx = name.lastIndexOf(".");
        return idx == -1 ? name : name.substring(0, idx);
    }

    private String normalize(String s) {
        return s.toLowerCase()
            .replace(" ", "_")
            .replaceAll("[^a-z0-9_]", "");
    }

    // =====================================================================================
    // 🧠 Release "principal" por projeto (usado pelas abas executivas)
    // =====================================================================================
    private Map<String, String> buildReleaseByProjectMap(
        Map<String, List<KPIData>> kpisByProject,
        String fallback
    ) {
        Map<String, String> map = new LinkedHashMap<>();

        for (String project : kpisByProject.keySet()) {

            String release =
                kpisByProject.get(project).stream()
                    .filter(k -> k.getGroup() != null && !k.getGroup().isEmpty())
                    .map(KPIData::getGroup)
                    .findFirst()
                    .orElse(fallback);

            map.put(project, release);
        }

        return map;
    }

    // =====================================================================================
    // 🗂 Histórico RUN-BASED (agora por release)
    // =====================================================================================
    private void generateRunBasedHistory(
        Map<String, JSONObject> consolidated,
        Map<String, JSONArray> defects,
        Map<String, JSONObject> functional,
        Path finalPath,
        Map<String, List<KPIData>> kpisByProject
    ) {
        LoggerUtils.section("📚 Salvando histórico RUN-BASED (multi-release)");

        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());

        for (String project : consolidated.keySet()) {

            List<KPIData> projectKpis = kpisByProject.get(project);
            if (projectKpis == null || projectKpis.isEmpty()) {
                LoggerUtils.warn("⚠ Nenhum KPI encontrado para " + project + " ao salvar histórico.");
                continue;
            }

            // releases distintas presentes nos KPIs
            Set<String> releases = new TreeSet<>(Comparator.reverseOrder());
            for (KPIData k : projectKpis) {
                String g = k.getGroup();
                if (g != null && !g.isEmpty()) {
                    releases.add(g);
                }
            }

            if (releases.isEmpty()) {
                LoggerUtils.warn("⚠ Nenhuma release em KPIs para " + project + " ao salvar histórico.");
                continue;
            }

            for (String releaseId : releases) {

                Path relDir =
                    Paths.get("historico", "releases", normalize(project), year, releaseId);

                Path snapDir =
                    Paths.get("historico", "snapshots", normalize(project), year, releaseId);

                try {
                    Files.createDirectories(relDir);
                    Files.createDirectories(snapDir);

                    JSONObject info = new JSONObject();
                    info.put("project", project);
                    info.put("releaseId", releaseId);
                    info.put("year", year);
                    info.put("generatedAt", now.toString());
                    info.put("reportFile", finalPath.getFileName().toString());

                    // consolidated filtrado por release
                    JSONObject fullConsolidated = consolidated.get(project);
                    JSONObject filteredConsolidated =
                        filterConsolidatedByRelease(fullConsolidated, releaseId);

                    writeJson(filteredConsolidated, snapDir.resolve("consolidated.json"));
                    writeJson(info, relDir.resolve("release_snapshot.json"));

                } catch (Exception e) {
                    LoggerUtils.error("⚠ Falha ao salvar histórico para " + project +
                        " / release " + releaseId, e);
                }
            }
        }
    }

    /**
     * Cria uma cópia do consolidated contendo apenas os Test Plans relacionados à release.
     * (mesma regra usada na KPIEngine: titulo do plano contém o releaseId)
     */
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        if (full == null) return null;

        JSONObject filtered = new JSONObject(full.toString()); // deep clone

        JSONArray originalPlans = full.optJSONArray("plan");
        JSONArray filteredPlans = new JSONArray();

        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");
                if (title.contains(releaseId)) {
                    filteredPlans.put(p);
                }
            }
        }

        filtered.put("plan", filteredPlans);
        return filtered;
    }

    private void writeJson(JSONObject json, Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(json.toString(2));
        } catch (Exception e) {
            LoggerUtils.error("❌ Erro ao salvar JSON em " + path, e);
        }
    }
}
