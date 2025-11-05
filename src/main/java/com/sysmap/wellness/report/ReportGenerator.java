package com.sysmap.wellness.report;

import com.sysmap.wellness.report.sheet.FunctionalSummarySheet;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/**
 * Classe principal de geração de relatórios.
 * Orquestra a criação das abas (sheets) no Excel, mantendo compatibilidade com o modelo anterior.
 */
public class ReportGenerator {

    /**
     * Gera o relatório final consolidado com todas as abas.
     */
    public void generateReport(Map<String, JSONObject> consolidatedData, Path outputPath) {
        try {
            // 🗂️ Define o diretório padrão de saída (fora do target)
            Path reportsDir = Path.of("output", "reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
                LoggerUtils.step("📁 Diretório criado: " + reportsDir.toAbsolutePath());
            }

            // 🔧 Ajusta o caminho final do arquivo dentro da pasta de relatórios
            Path finalPath = reportsDir.resolve(outputPath.getFileName());
            LoggerUtils.step("🧩 Gerando relatório final: " + finalPath.getFileName());

            try (Workbook wb = new XSSFWorkbook()) {
                // 1️⃣ Abas principais
                new FunctionalSummarySheet().create(wb, consolidatedData);
                // new TestExecutionTrendSheet().create(wb, consolidatedData);

                // 2️⃣ Ajuste automático das colunas
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet sheet = wb.getSheetAt(i);
                    if (sheet.getRow(0) != null) {
                        int cols = sheet.getRow(0).getPhysicalNumberOfCells();
                        for (int c = 0; c < cols; c++) {
                            sheet.autoSizeColumn(c);
                        }
                    }
                }

                // 3️⃣ Salva o arquivo Excel
                try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
                    wb.write(fos);
                }

                LoggerUtils.success("📊 Relatório Excel gerado com sucesso em: " + finalPath.toAbsolutePath());
                MetricsCollector.set("reportFile", finalPath.getFileName().toString());

            }
        } catch (IOException e) {
            LoggerUtils.error("Erro ao gerar relatório final (I/O)", e);
            MetricsCollector.increment("reportErrors");
        } catch (Exception e) {
            LoggerUtils.error("Erro inesperado ao gerar relatório final", e);
            MetricsCollector.increment("reportErrors");
        }
    }
}
