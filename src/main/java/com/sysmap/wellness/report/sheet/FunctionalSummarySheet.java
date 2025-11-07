package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Responsável por gerar a aba <b>"Resumo por Funcionalidade"</b> no relatório Excel.
 * <p>
 * Esta planilha apresenta uma visão consolidada de execução e defeitos
 * agrupados por funcionalidade (suite) de cada projeto processado.
 * </p>
 *
 * <p>Os dados utilizados nesta planilha são previamente processados
 * pela classe {@link com.sysmap.wellness.report.service.FunctionalSummaryService}.</p>
 *
 * <p>Para cada projeto, são exibidas as seguintes informações por funcionalidade:</p>
 * <ul>
 *   <li>Total de casos</li>
 *   <li>Casos executados, aprovados, falhados, abortados e não executados</li>
 *   <li>Percentual de execução</li>
 *   <li>Total de bugs, abertos, fechados, ignorados e percentual de bugs</li>
 * </ul>
 *
 * <p>Também são incluídos totais por projeto e um total geral consolidado no final da aba.</p>
 */
public class FunctionalSummarySheet {

    /**
     * Cria e popula a planilha "Resumo por Funcionalidade" dentro do {@link Workbook}.
     *
     * @param wb            O workbook (arquivo Excel) onde a aba será criada.
     * @param processedData Mapa contendo os dados processados por projeto.
     *                      Cada valor deve conter um {@link JSONObject} com o array "functionalities",
     *                      representando os resumos por funcionalidade gerados pelo
     *                      {@link com.sysmap.wellness.report.service.FunctionalSummaryService}.
     */
    public void create(Workbook wb, Map<String, JSONObject> processedData) {
        Sheet sheet = wb.createSheet("Resumo por Funcionalidade");
        Row header = sheet.createRow(0);

        String[] cols = {
                "Projeto", "Funcionalidade", "Total Cases", "Executados",
                "Passaram", "Falharam", "Não Executados", "Abortados", "% Executado",
                "Bugs Totais", "Bugs Abertos", "Bugs Fechados", "Bugs Ignorados", "% Bugs"
        };

        // === Criação dos estilos utilizando ExcelStyleFactory ===
        CellStyle leftStyle = ExcelStyleFactory.createStyle(wb, false, false, HorizontalAlignment.LEFT);
        CellStyle leftBoldStyle = ExcelStyleFactory.createStyle(wb, true, false, HorizontalAlignment.LEFT);
        CellStyle centerStyle = ExcelStyleFactory.createStyle(wb, false, false, HorizontalAlignment.CENTER);
        CellStyle boldCenter = ExcelStyleFactory.createStyle(wb, true, false, HorizontalAlignment.CENTER);
        CellStyle totalStyle = ExcelStyleFactory.createStyle(wb, true, true, HorizontalAlignment.CENTER);

        // === Cabeçalho ===
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(boldCenter);
        }

        int rowIdx = 1;

        // === Variáveis de totalização global ===
        int globalCases = 0, globalExec = 0, globalPass = 0, globalFail = 0, globalNotExec = 0, globalAborted = 0;
        int globalBugsTotal = 0, globalOpen = 0, globalClosed = 0, globalIgnored = 0;

        // === Processamento de cada projeto ===
        for (var entry : processedData.entrySet()) {
            String projectName = entry.getKey();
            JSONObject projectData = entry.getValue();

            JSONArray functionalities = projectData.optJSONArray("functionalities");
            if (functionalities == null || functionalities.isEmpty()) continue;

            // Totais do projeto
            int totalCasesProj = 0, executedProj = 0, passedProj = 0, failedProj = 0, abortedProj = 0;
            int notExecProj = 0, bugsTotalProj = 0, bugsOpenProj = 0, bugsClosedProj = 0, bugsIgnoredProj = 0;
            int functionalitiesCount = functionalities.length();

            // === Itera pelas funcionalidades do projeto ===
            for (int i = 0; i < functionalities.length(); i++) {
                JSONObject s = functionalities.getJSONObject(i);
                String funcName = s.optString("suiteName", "<sem título>");

                Row row = sheet.createRow(rowIdx++);
                int col = 0;

                // Projeto e nome da funcionalidade
                Cell projCell = row.createCell(col++);
                projCell.setCellValue(projectName);
                projCell.setCellStyle(leftStyle);

                Cell funcCell = row.createCell(col++);
                funcCell.setCellValue(funcName);
                funcCell.setCellStyle(leftStyle);

                // Métricas quantitativas
                row.createCell(col++).setCellValue(s.optInt("totalCases"));
                row.createCell(col++).setCellValue(s.optInt("executed"));
                row.createCell(col++).setCellValue(s.optInt("passed"));
                row.createCell(col++).setCellValue(s.optInt("failed"));
                row.createCell(col++).setCellValue(s.optInt("notExecuted"));
                row.createCell(col++).setCellValue(s.optInt("aborted"));
                row.createCell(col++).setCellValue(s.optInt("percExec") + "%");
                row.createCell(col++).setCellValue(s.optInt("bugsTotal"));
                row.createCell(col++).setCellValue(s.optInt("bugsOpen"));
                row.createCell(col++).setCellValue(s.optInt("bugsClosed"));
                row.createCell(col++).setCellValue(s.optInt("bugsIgnored"));
                row.createCell(col++).setCellValue(s.optInt("percBugs") + "%");

                // Centraliza células numéricas
                for (int c = 2; c < cols.length; c++)
                    row.getCell(c).setCellStyle(centerStyle);

                // Soma nos totais do projeto
                totalCasesProj += s.optInt("totalCases");
                executedProj += s.optInt("executed");
                passedProj += s.optInt("passed");
                failedProj += s.optInt("failed");
                abortedProj += s.optInt("aborted");
                notExecProj += s.optInt("notExecuted");
                bugsTotalProj += s.optInt("bugsTotal");
                bugsOpenProj += s.optInt("bugsOpen");
                bugsClosedProj += s.optInt("bugsClosed");
                bugsIgnoredProj += s.optInt("bugsIgnored");
            }

            // === Linha de totalização do projeto ===
            Row totalRow = sheet.createRow(rowIdx++);
            int col = 0;

            Cell totalLabel = totalRow.createCell(col++);
            totalLabel.setCellValue("TOTAL " + projectName);
            totalLabel.setCellStyle(leftBoldStyle);

            Cell funcTotal = totalRow.createCell(col++);
            funcTotal.setCellValue(functionalitiesCount + " Funcionalidades");
            funcTotal.setCellStyle(leftBoldStyle);

            totalRow.createCell(col++).setCellValue(totalCasesProj);
            totalRow.createCell(col++).setCellValue(executedProj);
            totalRow.createCell(col++).setCellValue(passedProj);
            totalRow.createCell(col++).setCellValue(failedProj);
            totalRow.createCell(col++).setCellValue(notExecProj);
            totalRow.createCell(col++).setCellValue(abortedProj);
            totalRow.createCell(col++).setCellValue(Math.round(executedProj * 100.0 / totalCasesProj) + "%");
            totalRow.createCell(col++).setCellValue(bugsTotalProj);
            totalRow.createCell(col++).setCellValue(bugsOpenProj);
            totalRow.createCell(col++).setCellValue(bugsClosedProj);
            totalRow.createCell(col++).setCellValue(bugsIgnoredProj);
            totalRow.createCell(col++).setCellValue(Math.round(bugsTotalProj * 100.0 / executedProj) + "%");

            for (int c = 2; c < cols.length; c++)
                totalRow.getCell(c).setCellStyle(totalStyle);

            // Totais globais acumulados
            globalCases += totalCasesProj;
            globalExec += executedProj;
            globalPass += passedProj;
            globalFail += failedProj;
            globalNotExec += notExecProj;
            globalAborted += abortedProj;
            globalBugsTotal += bugsTotalProj;
            globalOpen += bugsOpenProj;
            globalClosed += bugsClosedProj;
            globalIgnored += bugsIgnoredProj;

            rowIdx++;
        }

        // === Linha final de total geral ===
        Row globalRow = sheet.createRow(rowIdx++);
        int col = 0;
        Cell globalLabel = globalRow.createCell(col++);
        globalLabel.setCellValue("TOTAL GERAL");
        globalLabel.setCellStyle(leftBoldStyle);

        globalRow.createCell(col++).setCellValue("");
        globalRow.createCell(col++).setCellValue(globalCases);
        globalRow.createCell(col++).setCellValue(globalExec);
        globalRow.createCell(col++).setCellValue(globalPass);
        globalRow.createCell(col++).setCellValue(globalFail);
        globalRow.createCell(col++).setCellValue(globalNotExec);
        globalRow.createCell(col++).setCellValue(globalAborted);
        globalRow.createCell(col++).setCellValue(Math.round(globalExec * 100.0 / globalCases) + "%");
        globalRow.createCell(col++).setCellValue(globalBugsTotal);
        globalRow.createCell(col++).setCellValue(globalOpen);
        globalRow.createCell(col++).setCellValue(globalClosed);
        globalRow.createCell(col++).setCellValue(globalIgnored);
        globalRow.createCell(col++).setCellValue(Math.round(globalBugsTotal * 100.0 / globalExec) + "%");

        for (int c = 2; c < cols.length; c++)
            globalRow.getCell(c).setCellStyle(totalStyle);

        LoggerUtils.success("✔ Planilha 'Resumo por Funcionalidade' criada com sucesso.");
        MetricsCollector.increment("functionalSummarySheetsCreated");
    }
}
