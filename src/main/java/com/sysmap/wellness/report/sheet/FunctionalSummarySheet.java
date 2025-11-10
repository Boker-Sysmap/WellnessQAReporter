package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Cria a aba "Resumo por Funcionalidade" do relatório Excel,
 * aplicando estilos padronizados via {@link ReportStyleManager}.
 */
public class FunctionalSummarySheet {

    public void create(Workbook wb, Map<String, JSONObject> processedData) {
        LoggerUtils.step("📊 Criando planilha: Resumo por Funcionalidade");

        ReportStyleManager styles = ReportStyleManager.from(wb);
        Sheet sheet = wb.createSheet("Resumo por Funcionalidade");
        Row header = sheet.createRow(0);

        String[] cols = {
                "Projeto", "Funcionalidade", "Total Cases", "Executados",
                "Passaram", "Falharam", "Não Executados", "Abortados",
                "% Executado", "Bugs Totais", "Bugs Abertos", "Bugs Fechados",
                "Bugs Ignorados", "% Bugs"
        };

        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(styles.get("header"));
        }

        int rowIdx = 1;
        int globalCases = 0, globalExec = 0, globalPass = 0, globalFail = 0, globalNotExec = 0, globalAborted = 0;
        int globalBugsTotal = 0, globalOpen = 0, globalClosed = 0, globalIgnored = 0;

        for (var entry : processedData.entrySet()) {
            String projectName = entry.getKey();
            JSONObject projectData = entry.getValue();
            JSONArray funcs = projectData.optJSONArray("functionalities");
            if (funcs == null || funcs.isEmpty()) continue;

            int totalCasesProj = 0, executedProj = 0, passedProj = 0, failedProj = 0, abortedProj = 0;
            int notExecProj = 0, bugsTotalProj = 0, bugsOpenProj = 0, bugsClosedProj = 0, bugsIgnoredProj = 0;

            for (int i = 0; i < funcs.length(); i++) {
                JSONObject f = funcs.getJSONObject(i);
                Row row = sheet.createRow(rowIdx++);
                int col = 0;

                Cell projCell = row.createCell(col++);
                projCell.setCellValue(projectName);
                projCell.setCellStyle(styles.get("left"));

                Cell funcCell = row.createCell(col++);
                funcCell.setCellValue(f.optString("suiteName", "<sem título>"));
                funcCell.setCellStyle(styles.get("left"));

                row.createCell(col++).setCellValue(f.optInt("totalCases"));
                row.createCell(col++).setCellValue(f.optInt("executed"));
                row.createCell(col++).setCellValue(f.optInt("passed"));
                row.createCell(col++).setCellValue(f.optInt("failed"));
                row.createCell(col++).setCellValue(f.optInt("notExecuted"));
                row.createCell(col++).setCellValue(f.optInt("aborted"));
                row.createCell(col++).setCellValue(f.optInt("percExec") + "%");
                row.createCell(col++).setCellValue(f.optInt("bugsTotal"));
                row.createCell(col++).setCellValue(f.optInt("bugsOpen"));
                row.createCell(col++).setCellValue(f.optInt("bugsClosed"));
                row.createCell(col++).setCellValue(f.optInt("bugsIgnored"));
                row.createCell(col++).setCellValue(f.optInt("percBugs") + "%");

                for (int c = 2; c < cols.length; c++)
                    row.getCell(c).setCellStyle(styles.get("center"));

                totalCasesProj += f.optInt("totalCases");
                executedProj += f.optInt("executed");
                passedProj += f.optInt("passed");
                failedProj += f.optInt("failed");
                abortedProj += f.optInt("aborted");
                notExecProj += f.optInt("notExecuted");
                bugsTotalProj += f.optInt("bugsTotal");
                bugsOpenProj += f.optInt("bugsOpen");
                bugsClosedProj += f.optInt("bugsClosed");
                bugsIgnoredProj += f.optInt("bugsIgnored");
            }

            Row totalRow = sheet.createRow(rowIdx++);
            int col = 0;
            totalRow.createCell(col++).setCellValue("TOTAL " + projectName);
            totalRow.getCell(0).setCellStyle(styles.get("leftBold"));

            totalRow.createCell(col++).setCellValue(funcs.length() + " Funcionalidades");
            totalRow.getCell(1).setCellStyle(styles.get("leftBold"));

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
                totalRow.getCell(c).setCellStyle(styles.get("total"));

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

        Row globalRow = sheet.createRow(rowIdx++);
        globalRow.createCell(0).setCellValue("TOTAL GERAL");
        globalRow.getCell(0).setCellStyle(styles.get("leftBold"));
        globalRow.createCell(1).setCellValue("");
        globalRow.createCell(2).setCellValue(globalCases);
        globalRow.createCell(3).setCellValue(globalExec);
        globalRow.createCell(4).setCellValue(globalPass);
        globalRow.createCell(5).setCellValue(globalFail);
        globalRow.createCell(6).setCellValue(globalNotExec);
        globalRow.createCell(7).setCellValue(globalAborted);
        globalRow.createCell(8).setCellValue(Math.round(globalExec * 100.0 / globalCases) + "%");
        globalRow.createCell(9).setCellValue(globalBugsTotal);
        globalRow.createCell(10).setCellValue(globalOpen);
        globalRow.createCell(11).setCellValue(globalClosed);
        globalRow.createCell(12).setCellValue(globalIgnored);
        globalRow.createCell(13).setCellValue(Math.round(globalBugsTotal * 100.0 / globalExec) + "%");

        for (int c = 2; c < cols.length; c++)
            globalRow.getCell(c).setCellStyle(styles.get("total"));

        MetricsCollector.increment("functionalSummarySheetsCreated");
        LoggerUtils.success("✔ Planilha 'Resumo por Funcionalidade' criada com sucesso.");
    }
}
