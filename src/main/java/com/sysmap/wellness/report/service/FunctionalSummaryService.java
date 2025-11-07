package com.sysmap.wellness.report.service;

import com.sysmap.wellness.util.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço que aplica regras de negócio para o relatório "Resumo por Funcionalidade".
 * Gera um resumo consolidado de casos, execuções e defeitos por funcionalidade (suite).
 */
public class FunctionalSummaryService {

    public Map<String, JSONObject> prepareData(Map<String, JSONObject> consolidatedData) {
        Map<String, JSONObject> summaries = new HashMap<>();

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            JSONArray cases = safeArray(projectData, "case", "cases");
            JSONArray results = safeArray(projectData, "result", "results");
            JSONArray defects = safeArray(projectData, "defect", "defects");
            JSONArray suites = safeArray(projectData, "suite", "suites");

            JSONArray functionalities = generateFunctionalSummary(projectKey, cases, results, defects, suites);

            JSONObject summary = new JSONObject();
            summary.put("functionalities", functionalities);
            summaries.put(projectKey, summary);

            LoggerUtils.step("✅ Resumo gerado para o projeto: " + projectKey);
        }

        return summaries;
    }

    private JSONArray generateFunctionalSummary(String projectKey, JSONArray cases, JSONArray results, JSONArray defects, JSONArray suites) {
        Map<String, JSONObject> summaryMap = new HashMap<>();

        // Inicializa linhas por suite
        for (int i = 0; i < suites.length(); i++) {
            JSONObject suite = suites.getJSONObject(i);
            String suiteName = suite.optString("title", "Sem Nome");
            summaryMap.put(suiteName, baseRow(suiteName));
        }

        // Casos
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String suiteName = findSuiteName(c.optInt("suite_id"), suites);

            JSONObject s = summaryMap.get(suiteName);
            if (s == null) {
                s = baseRow(suiteName);
                summaryMap.put(suiteName, s);
            }
            s.put("totalCases", s.getInt("totalCases") + 1);
        }

        // Resultados
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String status = r.optString("status", "untested");
            int caseId = r.optInt("case_id", -1);

            JSONObject relatedCase = findCaseById(cases, caseId);
            String suiteName = findSuiteName(relatedCase.optInt("suite_id"), suites);

            JSONObject s = summaryMap.get(suiteName);
            if (s == null) {
                s = baseRow(suiteName);
                summaryMap.put(suiteName, s);
            }

            switch (status.toLowerCase()) {
                case "passed":
                    s.put("passed", s.getInt("passed") + 1);
                    s.put("executed", s.getInt("executed") + 1);
                    break;
                case "failed":
                    s.put("failed", s.getInt("failed") + 1);
                    s.put("executed", s.getInt("executed") + 1);
                    break;
                case "blocked":
                case "skipped":
                    s.put("notExecuted", s.getInt("notExecuted") + 1);
                    break;
                case "retest":
                case "aborted":
                    s.put("aborted", s.getInt("aborted") + 1);
                    s.put("executed", s.getInt("executed") + 1);
                    break;
                default:
                    s.put("notExecuted", s.getInt("notExecuted") + 1);
                    break;
            }
        }

        // Defeitos
        for (int i = 0; i < defects.length(); i++) {
            JSONObject d = defects.getJSONObject(i);
            String status = d.optString("status", "undefined");
            int caseId = d.optInt("case_id", -1);

            String suiteName = "Geral";
            if (caseId > 0) {
                JSONObject relatedCase = findCaseById(cases, caseId);
                suiteName = findSuiteName(relatedCase.optInt("suite_id"), suites);
            }

            JSONObject s = summaryMap.get(suiteName);
            if (s == null) {
                s = baseRow(suiteName);
                summaryMap.put(suiteName, s);
            }

            s.put("bugsTotal", s.getInt("bugsTotal") + 1);

            if (status.toLowerCase().contains("open") || status.toLowerCase().contains("new")) {
                s.put("bugsOpen", s.getInt("bugsOpen") + 1);
            } else if (status.toLowerCase().contains("closed") || status.toLowerCase().contains("resolved")) {
                s.put("bugsClosed", s.getInt("bugsClosed") + 1);
            } else {
                s.put("bugsIgnored", s.getInt("bugsIgnored") + 1);
            }
        }

        // Calcula percentuais
        JSONArray rows = new JSONArray();
        for (JSONObject s : summaryMap.values()) {
            int totalCases = s.getInt("totalCases");
            int executed = s.getInt("executed");
            int bugsTotal = s.getInt("bugsTotal");

            double percExec = totalCases > 0 ? (executed * 100.0 / totalCases) : 0.0;
            double percBugs = executed > 0 ? (bugsTotal * 100.0 / executed) : 0.0;

            s.put("percExec", Math.round(percExec));
            s.put("percBugs", Math.round(percBugs));

            rows.put(s);
        }

        return rows;
    }

    private JSONObject baseRow(String suiteName) {
        JSONObject o = new JSONObject();
        o.put("suiteName", suiteName);
        o.put("totalCases", 0);
        o.put("executed", 0);
        o.put("passed", 0);
        o.put("failed", 0);
        o.put("notExecuted", 0);
        o.put("aborted", 0);
        o.put("bugsTotal", 0);
        o.put("bugsOpen", 0);
        o.put("bugsClosed", 0);
        o.put("bugsIgnored", 0);
        o.put("percExec", 0);
        o.put("percBugs", 0);
        return o;
    }

    private String findSuiteName(int suiteId, JSONArray suites) {
        for (int i = 0; i < suites.length(); i++) {
            JSONObject s = suites.getJSONObject(i);
            if (s.optInt("id") == suiteId) {
                return s.optString("title", "Sem Nome");
            }
        }
        return "Sem Nome";
    }

    private JSONObject findCaseById(JSONArray cases, int id) {
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            if (c.optInt("id") == id) {
                return c;
            }
        }
        return new JSONObject();
    }

    private JSONArray safeArray(JSONObject source, String... keys) {
        for (String key : keys) {
            if (source.has(key)) {
                Object val = source.get(key);
                if (val instanceof JSONArray) return (JSONArray) val;
                if (val instanceof JSONObject) return new JSONArray().put(val);
            }
        }
        return new JSONArray();
    }
}
