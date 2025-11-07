package com.sysmap.wellness.report.service;

import com.sysmap.wellness.util.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço responsável por gerar o relatório <b>"Resumo por Funcionalidade"</b>,
 * que consolida informações de casos de teste, execuções e defeitos
 * agrupadas por funcionalidade (suite).
 *
 * <p>Este serviço atua sobre os dados já consolidados pelo
 * {@link com.sysmap.wellness.service.DataConsolidator}, aplicando regras de
 * negócio específicas para o nível de resumo.</p>
 *
 * <h2>Funções principais:</h2>
 * <ul>
 *   <li>Agrupar casos, execuções e defeitos por suíte (funcionalidade);</li>
 *   <li>Contabilizar métricas de execução, sucesso e falha;</li>
 *   <li>Calcular percentuais de execução e incidência de defeitos;</li>
 *   <li>Gerar um {@link JSONObject} final contendo o resumo estruturado.</li>
 * </ul>
 *
 * <p>Os resultados gerados são posteriormente consumidos pelo módulo de
 * geração de relatórios em Excel ({@link com.sysmap.wellness.report.ReportGenerator}).</p>
 *
 * @author Roberto
 * @version 1.1
 * @since 1.0
 */
public class FunctionalSummaryService {

    /**
     * Prepara os dados de resumo funcional para todos os projetos do conjunto consolidado.
     *
     * <p>Para cada projeto, este método identifica as suítes de teste e consolida
     * as métricas principais (casos, resultados e defeitos), retornando um
     * {@link JSONObject} com uma lista de funcionalidades e suas estatísticas.</p>
     *
     * @param consolidatedData Mapa contendo os dados consolidados por projeto.
     *                         Exemplo de estrutura: <code>projectKey → {"cases":[...],"results":[...],"defects":[...]}</code>
     * @return Mapa de projetos → {@link JSONObject} contendo o resumo de funcionalidades.
     */
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

    /**
     * Gera o resumo funcional consolidado de um único projeto.
     *
     * <p>Este método percorre os arrays de casos, resultados e defeitos, agrupando-os
     * por suíte. Também calcula percentuais de execução e incidência de bugs.</p>
     *
     * @param projectKey Identificador do projeto.
     * @param cases      Lista de casos de teste.
     * @param results    Lista de resultados de execução.
     * @param defects    Lista de defeitos associados.
     * @param suites     Lista de suítes (funcionalidades).
     * @return {@link JSONArray} contendo o resumo por funcionalidade.
     */
    private JSONArray generateFunctionalSummary(String projectKey, JSONArray cases, JSONArray results, JSONArray defects, JSONArray suites) {
        Map<String, JSONObject> summaryMap = new HashMap<>();

        // === Inicializa linhas base por suite ===
        for (int i = 0; i < suites.length(); i++) {
            JSONObject suite = suites.getJSONObject(i);
            String suiteName = suite.optString("title", "Sem Nome");
            summaryMap.put(suiteName, baseRow(suiteName));
        }

        // === Contabiliza casos de teste ===
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String suiteName = findSuiteName(c.optInt("suite_id"), suites);

            JSONObject s = summaryMap.getOrDefault(suiteName, baseRow(suiteName));
            s.put("totalCases", s.getInt("totalCases") + 1);
            summaryMap.put(suiteName, s);
        }

        // === Contabiliza resultados de execução ===
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String status = r.optString("status", "untested");
            int caseId = r.optInt("case_id", -1);

            JSONObject relatedCase = findCaseById(cases, caseId);
            String suiteName = findSuiteName(relatedCase.optInt("suite_id"), suites);

            JSONObject s = summaryMap.getOrDefault(suiteName, baseRow(suiteName));

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
            summaryMap.put(suiteName, s);
        }

        // === Contabiliza defeitos ===
        for (int i = 0; i < defects.length(); i++) {
            JSONObject d = defects.getJSONObject(i);
            String status = d.optString("status", "undefined");
            int caseId = d.optInt("case_id", -1);

            String suiteName = "Geral";
            if (caseId > 0) {
                JSONObject relatedCase = findCaseById(cases, caseId);
                suiteName = findSuiteName(relatedCase.optInt("suite_id"), suites);
            }

            JSONObject s = summaryMap.getOrDefault(suiteName, baseRow(suiteName));

            s.put("bugsTotal", s.getInt("bugsTotal") + 1);

            if (status.toLowerCase().contains("open") || status.toLowerCase().contains("new")) {
                s.put("bugsOpen", s.getInt("bugsOpen") + 1);
            } else if (status.toLowerCase().contains("closed") || status.toLowerCase().contains("resolved")) {
                s.put("bugsClosed", s.getInt("bugsClosed") + 1);
            } else {
                s.put("bugsIgnored", s.getInt("bugsIgnored") + 1);
            }
            summaryMap.put(suiteName, s);
        }

        // === Calcula percentuais ===
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

    /**
     * Cria uma linha base do resumo com todos os campos numéricos inicializados.
     *
     * @param suiteName Nome da suíte (funcionalidade).
     * @return {@link JSONObject} com a estrutura base da linha de resumo.
     */
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

    /**
     * Localiza o nome da suíte de testes com base no ID.
     *
     * @param suiteId ID da suíte procurada.
     * @param suites  Lista de suítes.
     * @return Nome da suíte, ou "Sem Nome" se não encontrada.
     */
    private String findSuiteName(int suiteId, JSONArray suites) {
        for (int i = 0; i < suites.length(); i++) {
            JSONObject s = suites.getJSONObject(i);
            if (s.optInt("id") == suiteId) {
                return s.optString("title", "Sem Nome");
            }
        }
        return "Sem Nome";
    }

    /**
     * Busca um caso de teste pelo seu ID.
     *
     * @param cases Lista de casos de teste.
     * @param id    Identificador do caso.
     * @return {@link JSONObject} do caso encontrado ou objeto vazio se não houver correspondência.
     */
    private JSONObject findCaseById(JSONArray cases, int id) {
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            if (c.optInt("id") == id) {
                return c;
            }
        }
        return new JSONObject();
    }

    /**
     * Retorna um {@link JSONArray} seguro a partir de um {@link JSONObject},
     * mesmo que o campo seja nulo ou contenha apenas um objeto singular.
     *
     * @param source Objeto JSON de origem.
     * @param keys   Lista de possíveis chaves válidas (ordem de prioridade).
     * @return {@link JSONArray} correspondente ou vazio se nenhuma chave existir.
     */
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
