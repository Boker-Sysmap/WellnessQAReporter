package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.sheet.ExcelStyleFactory;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Gera a aba "Resumo por Funcionalidade" do relatório Excel.
 * Esta classe é equivalente à função createFunctionalSummarySheet()
 * do ReportGenerator original, apenas modularizada.
 */
public class FunctionalSummarySheet {

    public void create(Workbook wb, Map<String, JSONObject> data) {
        Sheet sheet = wb.createSheet("Resumo por Funcionalidade");
        Row header = sheet.createRow(0);

        String[] cols = {"Projeto", "Funcionalidade", "Total Cases", "Executados",
                "Passaram", "Falharam", "Não Executados", "Abortados", "% Executado",
                "Bugs Totais", "Bugs Abertos", "Bugs Fechados", "Bugs Ignorados", "% Bugs"};

        // --- Estilos via ExcelStyleFactory
        CellStyle leftStyle = ExcelStyleFactory.createStyle(wb, false, false, HorizontalAlignment.LEFT);
        CellStyle leftBoldStyle = ExcelStyleFactory.createStyle(wb, true, false, HorizontalAlignment.LEFT);
        CellStyle centerStyle = ExcelStyleFactory.createStyle(wb, false, false, HorizontalAlignment.CENTER);
        CellStyle boldCenter = ExcelStyleFactory.createStyle(wb, true, false, HorizontalAlignment.CENTER);
        CellStyle totalStyle = ExcelStyleFactory.createStyle(wb, true, true, HorizontalAlignment.CENTER);

        // Cabeçalho
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(boldCenter);
        }

        int rowIdx = 1;

        // Totais globais
        int globalCases = 0, globalExec = 0, globalPass = 0, globalFail = 0, globalNotExec = 0, globalAborted = 0;
        int globalBugsTotal = 0, globalOpen = 0, globalClosed = 0, globalIgnored = 0;

        for (var entry : data.entrySet()) {
            String projectName = entry.getKey();
            if ("CONSOLIDATED".equalsIgnoreCase(projectName)) continue;

            JSONObject proj = entry.getValue();
            if (!proj.has("cases") || !proj.has("suites")) continue;

            JSONArray cases = proj.optJSONArray("cases");
            JSONArray defects = proj.optJSONArray("defects");
            JSONArray suites = proj.optJSONArray("suites");
            JSONArray results = proj.optJSONArray("results");

            // --- Mapeia suite_id → título completo
            Map<Integer, JSONObject> suiteMap = new HashMap<>();
            for (int i = 0; i < suites.length(); i++) {
                JSONObject s = suites.getJSONObject(i);
                suiteMap.put(s.optInt("id"), s);
            }
            Map<Integer, String> suiteFullNames = new HashMap<>();
            for (JSONObject s : suiteMap.values()) {
                int id = s.optInt("id");
                suiteFullNames.put(id, buildFullSuiteName(id, suiteMap));
            }

            // --- Mapeia result.hash → case_id
            Map<String, Integer> resultToCase = new HashMap<>();
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.optJSONObject(i);
                    if (r == null) continue;
                    String hash = r.optString("hash");
                    int caseId = r.optInt("case_id", -1);
                    if (hash != null && !hash.isEmpty() && caseId > 0)
                        resultToCase.put(hash, caseId);
                }
            }

            Map<String, JSONObject> suiteStats = new TreeMap<>();

            // --- Casos por suite
            for (int i = 0; i < cases.length(); i++) {
                JSONObject tc = cases.optJSONObject(i);
                if (tc == null) continue;
                int suiteId = tc.optInt("suite_id", -1);
                String suiteName = suiteFullNames.getOrDefault(suiteId, "<sem título>");
                JSONObject stats = suiteStats.computeIfAbsent(suiteName, k -> createEmptyStats());
                stats.put("totalCases", stats.getInt("totalCases") + 1);
            }

            // --- Resultados: contabiliza aborted separadamente
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.optJSONObject(i);
                if (r == null) continue;

                int caseId = r.optInt("case_id", -1);
                if (caseId <= 0) continue;

                String status = r.optString("status", "untested").toLowerCase();
                int suiteId = findSuiteIdForCase(caseId, cases);
                String suiteName = suiteFullNames.getOrDefault(suiteId, "<sem título>");
                JSONObject stats = suiteStats.computeIfAbsent(suiteName, k -> createEmptyStats());

                switch (status) {
                    case "passed":
                        stats.put("executed", stats.getInt("executed") + 1);
                        stats.put("passed", stats.getInt("passed") + 1);
                        break;
                    case "failed":
                        stats.put("executed", stats.getInt("executed") + 1);
                        stats.put("failed", stats.getInt("failed") + 1);
                        break;
                    case "aborted":
                        stats.put("aborted", stats.getInt("aborted") + 1);
                        break;
                    default:
                        break;
                }
            }

            // --- Bugs: relaciona defect → result → case → suite
            if (defects != null) {
                for (int i = 0; i < defects.length(); i++) {
                    JSONObject d = defects.optJSONObject(i);
                    if (d == null) continue;

                    JSONArray defectResults = d.optJSONArray("results");
                    if (defectResults == null || defectResults.isEmpty()) continue;

                    Set<Integer> linkedCases = new HashSet<>();
                    for (int j = 0; j < defectResults.length(); j++) {
                        Object val = defectResults.get(j);
                        if (val instanceof String) {
                            String resultHash = (String) val;
                            if (resultToCase.containsKey(resultHash)) {
                                linkedCases.add(resultToCase.get(resultHash));
                            }
                        }
                    }

                    for (int caseId : linkedCases) {
                        int suiteId = findSuiteIdForCase(caseId, cases);
                        String suiteName = suiteFullNames.getOrDefault(suiteId, "<sem título>");
                        JSONObject stats = suiteStats.computeIfAbsent(suiteName, k -> createEmptyStats());
                        stats.put("bugsTotal", stats.getInt("bugsTotal") + 1);

                        String st = d.optString("status", "").toLowerCase();
                        if (st.equals("open") || st.equals("in progress"))
                            stats.put("bugsOpen", stats.getInt("bugsOpen") + 1);
                        else if (st.equals("resolved"))
                            stats.put("bugsClosed", stats.getInt("bugsClosed") + 1);
                        else if (st.equals("invalid"))
                            stats.put("bugsIgnored", stats.getInt("bugsIgnored") + 1);
                    }
                }
            }

            // --- Totais do projeto
            int totalCasesProj = 0, executedProj = 0, passedProj = 0, failedProj = 0, abortedProj = 0;
            int notExecProj = 0, bugsTotalProj = 0, bugsOpenProj = 0, bugsClosedProj = 0, bugsIgnoredProj = 0;
            int funcionalidadesCount = suiteStats.size();

            for (JSONObject s : suiteStats.values()) {
                int total = s.getInt("totalCases");
                int executed = s.getInt("executed");
                int aborted = s.getInt("aborted");
                int bugs = s.getInt("bugsTotal");

                s.put("notExecuted", Math.max(0, total - executed - aborted));
                s.put("percExec", total > 0 ? Math.round(executed * 100.0 / total) : 0);
                s.put("percBugs", executed > 0 ? Math.round(bugs * 100.0 / executed) : 0);

                totalCasesProj += total;
                executedProj += executed;
                passedProj += s.getInt("passed");
                failedProj += s.getInt("failed");
                abortedProj += aborted;
                notExecProj += s.getInt("notExecuted");
                bugsTotalProj += s.getInt("bugsTotal");
                bugsOpenProj += s.getInt("bugsOpen");
                bugsClosedProj += s.getInt("bugsClosed");
                bugsIgnoredProj += s.getInt("bugsIgnored");
            }

            // --- Linhas das suites
            for (var suiteEntry : suiteStats.entrySet()) {
                String suiteName = suiteEntry.getKey();
                JSONObject s = suiteEntry.getValue();
                Row row = sheet.createRow(rowIdx++);
                int col = 0;

                Cell projCell = row.createCell(col++);
                projCell.setCellValue(projectName);
                projCell.setCellStyle(leftStyle);

                Cell funcCell = row.createCell(col++);
                funcCell.setCellValue(suiteName);
                funcCell.setCellStyle(leftStyle);

                row.createCell(col++).setCellValue(s.getInt("totalCases"));
                row.createCell(col++).setCellValue(s.getInt("executed"));
                row.createCell(col++).setCellValue(s.getInt("passed"));
                row.createCell(col++).setCellValue(s.getInt("failed"));
                row.createCell(col++).setCellValue(s.getInt("notExecuted"));
                row.createCell(col++).setCellValue(s.getInt("aborted"));
                row.createCell(col++).setCellValue(s.getInt("percExec") + "%");
                row.createCell(col++).setCellValue(s.getInt("bugsTotal"));
                row.createCell(col++).setCellValue(s.getInt("bugsOpen"));
                row.createCell(col++).setCellValue(s.getInt("bugsClosed"));
                row.createCell(col++).setCellValue(s.getInt("bugsIgnored"));
                row.createCell(col++).setCellValue(s.getInt("percBugs") + "%");

                for (int c = 2; c < cols.length; c++)
                    row.getCell(c).setCellStyle(centerStyle);
            }

            // --- Total do projeto
            Row totalRow = sheet.createRow(rowIdx++);
            int col = 0;
            Cell totalLabel = totalRow.createCell(col++);
            totalLabel.setCellValue("TOTAL " + projectName);
            totalLabel.setCellStyle(leftBoldStyle);

            Cell funcTotal = totalRow.createCell(col++);
            funcTotal.setCellValue(funcionalidadesCount + " Funcionalidades");
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

            // --- Totais globais
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

        // --- TOTAL GERAL
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

    // ==== Métodos auxiliares ====

    private JSONObject createEmptyStats() {
        return new JSONObject()
                .put("totalCases", 0)
                .put("executed", 0)
                .put("passed", 0)
                .put("failed", 0)
                .put("aborted", 0)
                .put("notExecuted", 0)
                .put("bugsTotal", 0)
                .put("bugsOpen", 0)
                .put("bugsClosed", 0)
                .put("bugsIgnored", 0);
    }

    private String buildFullSuiteName(int id, Map<Integer, JSONObject> suiteMap) {
        List<String> chain = new ArrayList<>();
        Integer current = id;
        while (current != null && suiteMap.containsKey(current)) {
            JSONObject s = suiteMap.get(current);
            chain.add(s.optString("title", "<sem título>"));
            Object parent = s.opt("parent_id");
            current = parent instanceof Number ? ((Number) parent).intValue() : null;
        }
        Collections.reverse(chain);
        return String.join(" → ", chain);
    }

    private int findSuiteIdForCase(int caseId, JSONArray cases) {
        if (cases == null) return -1;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.optJSONObject(i);
            if (c != null && c.optInt("id") == caseId) {
                return c.optInt("suite_id", -1);
            }
        }
        return -1;
    }
}
