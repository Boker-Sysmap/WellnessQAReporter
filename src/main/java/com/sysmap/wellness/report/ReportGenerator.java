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
        long start = System.currentTimeMillis();

        try {
            // === 1️⃣ Diretório de saída ===
            Path reportsDir = Path.of("output", "reports");
            if (!Files.exists(reportsDir)) Files.createDirectories(reportsDir);

            Path finalPath = reportsDir.resolve(outputPath.getFileName());
            LoggerUtils.step("🧩 Gerando relatório final: " + finalPath.getFileName());

            // === 2️⃣ Serviços ===
            FunctionalSummaryService summaryService = new FunctionalSummaryService();
            DefectAnalyticalService defectService = new DefectAnalyticalService();
            KPIService kpiService = new KPIService();

            // Mantém KPIs de todos os projetos para o painel consolidado
            Map<String, List<KPIData>> kpisByProject = new LinkedHashMap<>();

            try (XSSFWorkbook wb = new XSSFWorkbook()) {

                // === 3️⃣ Painel Consolidado (será movido para o topo depois) ===
                // Só é possível gerar depois de ter os KPIs de todos os projetos,
                // então primeiro coletamos os dados individuais.
                for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
                    String projectCode = entry.getKey();
                    JSONObject projectData = entry.getValue();

                    LoggerUtils.step("\n📊 Gerando abas para o projeto: " + projectCode);

                    // --- KPIs Executivos ---
                    List<KPIData> kpis = kpiService.calculateKPIs(projectData, projectCode);
                    kpisByProject.put(projectCode, kpis);
                    ExecutiveKPISheet.create(wb, kpis, projectCode + " – Resumo Executivo");

                    // --- Resumo Funcional ---
                    Map<String, JSONObject> summary = summaryService.prepareData(Map.of(projectCode, projectData));
                    new FunctionalSummarySheet().create(wb, summary, projectCode + " – Resumo Funcional");

                    // --- Defeitos Analítico ---
                    Map<String, JSONArray> defects = defectService.prepareData(Map.of(projectCode, projectData));
                    new DefectAnalyticalReportSheet().create(wb, defects, projectCode + " – Defeitos Analítico");
                }

                // === 4️⃣ Painel Consolidado ===
                if (!kpisByProject.isEmpty()) {
                    ExecutiveConsolidatedSheet.create(wb, kpisByProject);
                    wb.setSheetOrder("Painel Consolidado", 0); // exibe primeiro
                    appendFooter(wb.getSheet("Painel Consolidado"), wb, start);
                }

                // === 5️⃣ Autoajuste de colunas ===
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

                // === 6️⃣ Grava o arquivo ===
                try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
                    wb.write(fos);
                }

                long duration = System.currentTimeMillis() - start;
                LoggerUtils.success("✅ Relatório Excel gerado com sucesso em: " + finalPath.toAbsolutePath());
                MetricsCollector.set("reportFile", finalPath.getFileName().toString());
                MetricsCollector.set("reportGenerationTimeMs", duration);

            }

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
