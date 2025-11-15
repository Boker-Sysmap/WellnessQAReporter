package com.sysmap.wellness.report;

import com.sysmap.wellness.report.service.*;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.sheet.*;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
 * ReportGenerator (RUN-BASED + PREMIUM)
 *
 * Esta versão integra:
 *  ✔ DefectAnalyticalService RUN-BASED
 *  ✔ KPIs
 *  ✔ Resumo Funcional
 *  ✔ Dashboard e Sintético
 *  ✔ Histórico completo por release
 *
 * A ordem das abas segue o modelo original:
 *  1 - Painel Consolidado
 *  2 - Resumo Executivo (por projeto)
 *  3 - Resumo Funcional
 *  4 - Defeitos Analítico
 *  5 - Defeitos Dashboard
 *  6 - Defeitos Sintético
 */
public class ReportGenerator {

    public void generateReport(Map<String, JSONObject> consolidatedData, Path outputPath) {

        LoggerUtils.section("GERAÇÃO DE RELATÓRIO (PREMIUM + RUN-BASED)");
        LoggerUtils.startTimer("report");
        long start = System.nanoTime();

        try {
            Path finalPath = prepareOutputPath(outputPath);

            FunctionalSummaryService summaryService = new FunctionalSummaryService();
            DefectAnalyticalService defectService = new DefectAnalyticalService();
            KPIService kpiService = new KPIService();

            // 1. KPIs
            LoggerUtils.section("KPIs");
            Map<String, List<KPIData>> kpisByProject =
                    calculateKPIsForAllProjects(consolidatedData, kpiService);

            // 2. Resumo Funcional
            LoggerUtils.section("RESUMO FUNCIONAL");
            Map<String, JSONObject> functionalSummaries =
                    summaryService.prepareData(consolidatedData);

            // 3. Defeitos ENRIQUECIDOS (run-based)
            LoggerUtils.section("DEFEITOS (RUN-BASED)");
            Map<String, JSONArray> enrichedDefects =
                    defectService.prepareData(consolidatedData);

            // 4. Gera o Excel
            try (XSSFWorkbook wb = new XSSFWorkbook()) {

                generateAllSheets(
                        wb,
                        consolidatedData,
                        enrichedDefects,
                        kpisByProject,
                        functionalSummaries
                );

                adjustAllColumns(wb);

                saveWorkbook(wb, finalPath);
                generateHistoryFiles(consolidatedData, enrichedDefects, kpisByProject, functionalSummaries, finalPath);

            }

            long end = System.nanoTime();
            LoggerUtils.success("🏁 Relatório gerado: " + finalPath.toAbsolutePath());
            MetricsCollector.timing("report.totalMs", (end - start) / 1_000_000);

        } catch (Exception e) {
            LoggerUtils.error("💥 Erro grave durante a geração do relatório", e);
            MetricsCollector.increment("reportErrors");
        }
    }

    // ---------------------------------------------------------------------
    // PREPARA CAMINHO DE SAÍDA
    // ---------------------------------------------------------------------
    private Path prepareOutputPath(Path outputPath) throws IOException {

        Path reportsDir = Path.of("output", "reports");
        if (!Files.exists(reportsDir)) Files.createDirectories(reportsDir);

        Path finalPath = reportsDir.resolve(outputPath.getFileName());
        LoggerUtils.step("📄 Arquivo final: " + finalPath.getFileName());

        return finalPath;
    }

    // ---------------------------------------------------------------------
    // KPIs
    // ---------------------------------------------------------------------
    private Map<String, List<KPIData>> calculateKPIsForAllProjects(
            Map<String, JSONObject> consolidatedData,
            KPIService kpiService
    ) {
        Map<String, List<KPIData>> kpisByProject = new LinkedHashMap<>();

        for (String project : consolidatedData.keySet()) {
            List<KPIData> list = kpiService.calculateKPIs(
                    consolidatedData.get(project),
                    project
            );
            kpisByProject.put(project, list);
        }

        return kpisByProject;
    }

    // ---------------------------------------------------------------------
    // GERA TODAS AS ABAS
    // ---------------------------------------------------------------------
    private void generateAllSheets(
            XSSFWorkbook wb,
            Map<String, JSONObject> consolidatedData,
            Map<String, JSONArray> enrichedDefects,
            Map<String, List<KPIData>> kpisByProject,
            Map<String, JSONObject> functionalSummaries
    ) {

        // 1 – Painel Consolidado
        LoggerUtils.section("📊 Painel Consolidado");
        ExecutiveConsolidatedSheet.create(wb, kpisByProject);
        wb.setSheetOrder("Painel Consolidado", 0);

        // 2 – Resumo Executivo
        LoggerUtils.section("📈 Resumo Executivo");
        for (String project : kpisByProject.keySet()) {
            ExecutiveKPISheet.create(
                    wb,
                    kpisByProject.get(project),
                    project + " – Resumo Executivo"
            );
        }

        // 3 – Resumo Funcional
        LoggerUtils.section("📘 Resumo Funcional");
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

        // 4 – Defeitos Analítico (USANDO enrichedDefects)
        LoggerUtils.section("🐞 Defeitos Analítico");
        for (String project : consolidatedData.keySet()) {

            JSONArray list = enrichedDefects.getOrDefault(project, new JSONArray());

            Map<String, JSONArray> map = new LinkedHashMap<>();
            map.put(project, list);

            new DefectAnalyticalReportSheet().create(
                    wb,
                    map,
                    project + " – Defeitos Analítico"
            );
        }

        // 5 – Dashboard (também usando enriched)
        LoggerUtils.section("📊 Dashboard de Defeitos");
        for (String project : consolidatedData.keySet()) {

            JSONArray defects = enrichedDefects.getOrDefault(project, new JSONArray());

            JSONObject d = new JSONObject();
            d.put("defects", defects);

            DefectsDashboardSheet.create(
                    wb,
                    d,
                    project + " – Defeitos DashBoard"
            );
        }

        // 6 – Sintético (também usando enriched)
        LoggerUtils.section("📋 Defeitos Sintético");
        for (String project : consolidatedData.keySet()) {

            JSONArray defects = enrichedDefects.getOrDefault(project, new JSONArray());

            JSONObject d = new JSONObject();
            d.put("defects", defects);

            DefectsSyntheticSheet.create(
                    wb,
                    d,
                    project + " – Defeitos Sintético"
            );
        }
    }

    // ---------------------------------------------------------------------
    // AUTOAJUSTE DE COLUNAS
    // ---------------------------------------------------------------------
    private void adjustAllColumns(Workbook wb) {

        LoggerUtils.step("🪛 Ajustando colunas");

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {

            Sheet s = wb.getSheetAt(i);
            if (s.getRow(0) == null) continue;

            int cols = s.getRow(0).getPhysicalNumberOfCells();

            for (int c = 0; c < cols; c++) {
                try {
                    s.autoSizeColumn(c);
                    int width = s.getColumnWidth(c);
                    s.setColumnWidth(c, Math.min(width + 1500, 18000));
                } catch (Exception ignored) {}
            }
        }
    }

    // ---------------------------------------------------------------------
    // SALVAR EXCEL
    // ---------------------------------------------------------------------
    private void saveWorkbook(Workbook wb, Path finalPath) throws IOException {

        try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
            wb.write(fos);
        }

        LoggerUtils.success("💾 Excel salvo em " + finalPath.toAbsolutePath());
        MetricsCollector.set("reportFile", finalPath.getFileName().toString());
    }

    // ---------------------------------------------------------------------
    // HISTÓRICO
    // ---------------------------------------------------------------------
    private void generateHistoryFiles(
            Map<String, JSONObject> consolidated,
            Map<String, JSONArray> enrichedDefects,
            Map<String, List<KPIData>> kpisByProject,
            Map<String, JSONObject> functionalSummaries,
            Path finalPath
    ) {

        LoggerUtils.section("📚 Salvando histórico (run-based)");

        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String releaseId = stripExt(finalPath.getFileName().toString());

        for (String project : consolidated.keySet()) {
            try {
                String normalized = normalize(project);

                Path relDir = Paths.get("historico", "releases", normalized, year, releaseId);
                Path snapDir = Paths.get("historico", "snapshots", normalized, year, releaseId);

                Files.createDirectories(relDir);
                Files.createDirectories(snapDir);

                JSONObject hist = new JSONObject();
                hist.put("project", project);
                hist.put("year", year);
                hist.put("releaseId", releaseId);
                hist.put("generatedAt", now.toString());
                hist.put("reportFile", finalPath.getFileName().toString());

                // KPIs
                JSONArray arrKpi = new JSONArray();
                List<KPIData> kpis = kpisByProject.get(project);
                if (kpis != null) {
                    for (KPIData k : kpis) arrKpi.put(k.toJson());
                }
                hist.put("kpis", arrKpi);

                // Resumo funcional
                if (functionalSummaries.containsKey(project)) {
                    hist.put("functionalSummary", functionalSummaries.get(project));
                }

                // Defeitos enriquecidos
                hist.put("defects", enrichedDefects.get(project));

                // Gravar histórico
                writeJson(hist, relDir.resolve("kpis_release.json"));
                writeJson(consolidated.get(project), snapDir.resolve("consolidated.json"));

                LoggerUtils.info("📁 Histórico salvo: " + project);

            } catch (Exception e) {
                LoggerUtils.error("⚠️ Falha ao salvar histórico para " + project, e);
            }
        }
    }

    private void writeJson(JSONObject json, Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(json.toString(2));
        } catch (Exception e) {
            LoggerUtils.error("Erro ao salvar JSON em " + path, e);
        }
    }

    private String stripExt(String f) {
        int idx = f.lastIndexOf(".");
        return idx == -1 ? f : f.substring(0, idx);
    }

    private String normalize(String s) {
        return s.toLowerCase()
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
}
