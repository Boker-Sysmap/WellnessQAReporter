package com.sysmap.wellness.report;

import com.sysmap.wellness.report.service.*;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.sheet.*;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gera o relatório Excel completo, com abas separadas por projeto
 * e um painel consolidado de KPIs entre projetos.
 */
public class ReportGenerator {

    public void generateReport(Map<String, JSONObject> consolidatedData, Path outputPath) {
        long globalStart = System.nanoTime();
        LoggerUtils.section("GERAÇÃO DE RELATÓRIO FINAL");
        LoggerUtils.startTimer("report");

        try {
            // === 1️⃣ Diretório de saída ===
            Path reportsDir = Path.of("output", "reports");
            if (!Files.exists(reportsDir)) Files.createDirectories(reportsDir);

            Path finalPath = reportsDir.resolve(outputPath.getFileName());
            LoggerUtils.step("🧩 Gerando relatório final: " + finalPath.getFileName());

            // === 2️⃣ Inicialização dos serviços ===
            FunctionalSummaryService summaryService = new FunctionalSummaryService();
            DefectAnalyticalService defectService = new DefectAnalyticalService();
            KPIService kpiService = new KPIService();

            Map<String, List<KPIData>> kpisByProject = new LinkedHashMap<>();
            int totalProjects = consolidatedData.size();
            int currentProject = 0;

            try (XSSFWorkbook wb = new XSSFWorkbook()) {

                // === 3️⃣ Geração das abas por projeto ===
                for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
                    long projectStart = System.nanoTime();
                    String projectCode = entry.getKey();
                    JSONObject projectData = entry.getValue();
                    currentProject++;

                    LoggerUtils.section("📊 Projeto " + projectCode + " (" + currentProject + "/" + totalProjects + ")");
                    LoggerUtils.startTimer(projectCode);

                    // --- KPIs Executivos ---
                    LoggerUtils.step("Calculando KPIs executivos...");
                    List<KPIData> kpis = kpiService.calculateKPIs(projectData, projectCode);
                    kpisByProject.put(projectCode, kpis);
                    ExecutiveKPISheet.create(wb, kpis, projectCode + " – Resumo Executivo");
                    LoggerUtils.success("Planilha 'Resumo Executivo' criada.");

                    // --- Resumo Funcional ---
                    LoggerUtils.step("Gerando Resumo Funcional...");
                    Map<String, JSONObject> summary = summaryService.prepareData(Map.of(projectCode, projectData));
                    new FunctionalSummarySheet().create(wb, summary, projectCode + " – Resumo Funcional");
                    LoggerUtils.success("Planilha 'Resumo Funcional' criada.");

                    // --- Defeitos Analítico ---
                    LoggerUtils.step("Gerando Defeitos Analítico...");
                    Map<String, JSONArray> defects = defectService.prepareData(Map.of(projectCode, projectData));
                    new DefectAnalyticalReportSheet().create(wb, defects, projectCode + " – Defeitos Analítico");
                    LoggerUtils.success("Planilha 'Defeitos Analítico' criada.");

                    LoggerUtils.endTimer(projectCode, "Projeto " + projectCode + " concluído");
                    LoggerUtils.progress("Progresso geral", currentProject, totalProjects);
                    LoggerUtils.time("Duração do projeto " + projectCode, projectStart);
                }

                // === 4️⃣ Painel Consolidado ===
                LoggerUtils.section("PAINEL CONSOLIDADO");
                if (!kpisByProject.isEmpty()) {
                    LoggerUtils.step("Criando aba de consolidação de KPIs...");
                    ExecutiveConsolidatedSheet.create(wb, kpisByProject);
                    wb.setSheetOrder("Painel Consolidado", 0);
                    appendFooter(wb.getSheet("Painel Consolidado"), wb, System.currentTimeMillis());
                    LoggerUtils.success("Painel Consolidado criado com sucesso.");
                }

                // === 5️⃣ Ajuste de colunas ===
                LoggerUtils.step("Ajustando largura de colunas...");
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet s = wb.getSheetAt(i);
                    if (s.getRow(0) != null) {
                        int cols = s.getRow(0).getPhysicalNumberOfCells();
                        for (int c = 0; c < cols; c++) {
                            s.autoSizeColumn(c);
                            int width = s.getColumnWidth(c);
                            s.setColumnWidth(c, Math.min(width + 1000, 15000));
                        }
                    }
                }

                // === 6️⃣ Gravação do arquivo ===
                try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
                    wb.write(fos);
                }

                long durationMs = (System.nanoTime() - globalStart) / 1_000_000;
                LoggerUtils.endTimer("report", "Relatório Excel gerado");
                LoggerUtils.success("✅ Relatório Excel salvo em: " + finalPath.toAbsolutePath());
                LoggerUtils.size(finalPath.getFileName().toString(), Files.size(finalPath));
                LoggerUtils.metric("reportGenerationTimeMs", durationMs);
                MetricsCollector.set("reportFile", finalPath.getFileName().toString());
                MetricsCollector.set("reportGenerationTimeMs", durationMs);

            }

            LoggerUtils.section("FINALIZAÇÃO");
            LoggerUtils.time("Duração total do processo", globalStart);
            LoggerUtils.success("Execução concluída sem erros.");

        } catch (IOException e) {
            LoggerUtils.error("💥 Erro ao gerar relatório (I/O)", e);
            MetricsCollector.increment("reportErrors");

        } catch (Exception e) {
            LoggerUtils.error("💥 Erro inesperado ao gerar relatório", e);
            MetricsCollector.increment("reportErrors");
        }
    }

    /**
     * Adiciona rodapé informativo na aba Painel Consolidado.
     */
    private void appendFooter(Sheet sheet, XSSFWorkbook wb, long startTime) {
        if (sheet == null) return;

        int footerRowNum = sheet.getLastRowNum() + 3;
        Row footer = sheet.createRow(footerRowNum);

        long duration = System.currentTimeMillis() - startTime;
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        String footerText = String.format(
                "Relatório gerado em %s | Duração total: %.2f segundos",
                timestamp, (duration / 1000.0)
        );

        Cell cell = footer.createCell(0);
        cell.setCellValue(footerText);

        CellStyle footerStyle = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        footerStyle.setFont(font);
        footerStyle.setAlignment(HorizontalAlignment.LEFT);

        cell.setCellStyle(footerStyle);
        sheet.addMergedRegion(new CellRangeAddress(footerRowNum, footerRowNum, 0, 3));
    }
}
