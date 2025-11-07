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
 * Classe responsável por **gerar o relatório Excel final** consolidando os dados obtidos da API Qase.
 * <p>
 * O {@code ReportGenerator} atua como orquestrador entre os serviços de processamento de dados e as
 * classes de geração de planilhas, criando as abas do arquivo Excel e salvando o resultado
 * no diretório configurado.
 * </p>
 *
 * <h3>Fluxo de execução:</h3>
 * <ol>
 *   <li>Cria (se necessário) o diretório de saída <code>output/reports</code>.</li>
 *   <li>Invoca o {@link FunctionalSummaryService} para consolidar dados de execução por funcionalidade.</li>
 *   <li>Invoca o {@link DefectAnalyticalService} para consolidar dados de defeitos.</li>
 *   <li>Cria as planilhas correspondentes utilizando:
 *       <ul>
 *           <li>{@link FunctionalSummarySheet} — Resumo por Funcionalidade</li>
 *           <li>{@link DefectAnalyticalReportSheet} — Gestão de Defeitos - Analítico</li>
 *       </ul>
 *   </li>
 *   <li>Autoajusta as colunas, grava o arquivo e registra métricas de execução.</li>
 * </ol>
 *
 * <p>Em caso de falhas de I/O ou erros inesperados, o processo é interrompido com logs de erro e
 * incremento de métricas de falhas.</p>
 */
public class ReportGenerator {

    /**
     * Gera o relatório Excel consolidando dados de execução e defeitos dos projetos.
     * <p>
     * O relatório final é salvo no diretório <code>output/reports</code> com o nome especificado
     * em {@code outputPath}.
     * </p>
     *
     * @param consolidatedData Mapa contendo os dados consolidados de todos os projetos.
     *                         A chave representa o código do projeto e o valor é um
     *                         {@link JSONObject} com os dados brutos (cases, results, defects etc.).
     * @param outputPath       Caminho completo (relativo ou absoluto) do arquivo Excel de saída.
     *
     * @throws IOException se ocorrer erro de leitura ou gravação de arquivos.
     * @throws Exception   se ocorrer qualquer outro erro inesperado durante a geração do relatório.
     */
    public void generateReport(Map<String, JSONObject> consolidatedData, Path outputPath) {
        long start = System.currentTimeMillis();

        try {
            // === 1️⃣ Criação do diretório de saída ===
            Path reportsDir = Path.of("output", "reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
                LoggerUtils.step("📁 Diretório criado: " + reportsDir.toAbsolutePath());
            }

            // Caminho final do arquivo Excel
            Path finalPath = reportsDir.resolve(outputPath.getFileName());
            LoggerUtils.step("🧩 Gerando relatório final: " + finalPath.getFileName());

            // === 2️⃣ Processa dados consolidados ===
            // Normaliza dados de execução (Resumo por Funcionalidade)
            FunctionalSummaryService summaryService = new FunctionalSummaryService();
            Map<String, JSONObject> processedData = summaryService.prepareData(consolidatedData);

            // Extrai e formata dados de defeitos (Gestão de Defeitos - Analítico)
            DefectAnalyticalService defectService = new DefectAnalyticalService();
            Map<String, JSONArray> defectData = defectService.prepareData(consolidatedData);

            // === 3️⃣ Cria o workbook (arquivo Excel em memória) ===
            try (Workbook wb = new XSSFWorkbook()) {

                // 3.1 Aba: Resumo por Funcionalidade
                new FunctionalSummarySheet().create(wb, processedData);

                // 3.2 Aba: Gestão de Defeitos - Analítico
                new DefectAnalyticalReportSheet().create(wb, defectData);

                // 3.3 Ajuste automático das colunas de cada aba
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet sheet = wb.getSheetAt(i);
                    if (sheet.getRow(0) != null) {
                        int cols = sheet.getRow(0).getPhysicalNumberOfCells();
                        for (int c = 0; c < cols; c++) {
                            sheet.autoSizeColumn(c);
                        }
                    }
                }

                // === 4️⃣ Grava o arquivo no disco ===
                try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
                    wb.write(fos);
                }

                // === 5️⃣ Métricas e logs ===
                long duration = System.currentTimeMillis() - start;
                LoggerUtils.success("📊 Relatório Excel gerado com sucesso em: " + finalPath.toAbsolutePath());
                LoggerUtils.metric("reportGenerationTimeMs", duration);
                MetricsCollector.set("reportFile", finalPath.getFileName().toString());
            }

        } catch (IOException e) {
            // Erros de entrada/saída
            LoggerUtils.error("💥 Erro ao gerar relatório (I/O)", e);
            MetricsCollector.increment("reportErrors");

        } catch (Exception e) {
            // Qualquer outro erro inesperado
            LoggerUtils.error("💥 Erro inesperado ao gerar relatório", e);
            MetricsCollector.increment("reportErrors");
        }
    }
}
