package com.sysmap.wellness.report;

import com.sysmap.wellness.report.service.FunctionalSummaryService;
import com.sysmap.wellness.report.service.DefectAnalyticalService;
import com.sysmap.wellness.report.sheet.FunctionalSummarySheet;
import com.sysmap.wellness.report.sheet.DefectAnalyticalReportSheet;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/**
 * Classe principal de geração de relatórios.
 * Orquestra a criação das abas (sheets) no Excel, aplicando regras de negócio via serviços.
 */
public class ReportGenerator {

    public void generateReport(Map<String, JSONObject> consolidatedData, Path outputPath) {
        long start = System.currentTimeMillis();

        try {
            Path reportsDir = Path.of("output", "reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
                LoggerUtils.step("📁 Diretório criado: " + reportsDir.toAbsolutePath());
            }

            Path finalPath = reportsDir.resolve(outputPath.getFileName());
            LoggerUtils.step("🧩 Gerando relatório final: " + finalPath.getFileName());

            // 🔹 Normaliza dados gerais (para Resumo por Funcionalidade)
            FunctionalSummaryService summaryService = new FunctionalSummaryService();
            Map<String, JSONObject> processedData = summaryService.prepareData(consolidatedData);

            // 🔹 Extrai e prepara dados específicos de defeitos
            DefectAnalyticalService defectService = new DefectAnalyticalService();
            Map<String, JSONArray> defectData = defectService.prepareData(consolidatedData);

            try (Workbook wb = new XSSFWorkbook()) {

                // 1️⃣ Aba "Resumo por Funcionalidade"
                new FunctionalSummarySheet().create(wb, processedData);

                // 2️⃣ Aba "Gestão de Defeitos - Analítico"
                new DefectAnalyticalReportSheet().create(wb, defectData);

                // 3️⃣ Ajuste automático de colunas
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet sheet = wb.getSheetAt(i);
                    if (sheet.getRow(0) != null) {
                        int cols = sheet.getRow(0).getPhysicalNumberOfCells();
                        for (int c = 0; c < cols; c++) {
                            sheet.autoSizeColumn(c);
                        }
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
                    wb.write(fos);
                }

                long duration = System.currentTimeMillis() - start;
                LoggerUtils.success("📊 Relatório Excel gerado com sucesso em: " + finalPath.toAbsolutePath());
                LoggerUtils.metric("reportGenerationTimeMs", duration);
                MetricsCollector.set("reportFile", finalPath.getFileName().toString());
            }

        } catch (IOException e) {
            LoggerUtils.error("💥 Erro ao gerar relatório (I/O)", e);
            MetricsCollector.increment("reportErrors");
        } catch (Exception e) {
            LoggerUtils.error("💥 Erro inesperado ao gerar relatório", e);
            MetricsCollector.increment("reportErrors");
        }
    }
}
