package com.sysmap.wellness.report.service;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço responsável por gerar o relatório <b>"Resumo por Funcionalidade"</b>,
 * consolidando informações de casos de teste, execuções e defeitos agrupadas
 * por suíte (funcionalidade).
 *
 * <p>Este serviço opera sobre os dados já unificados pelo DataConsolidator,
 * aplicando lógica de agregação e cálculo de métricas essenciais para fornecer
 * uma visão executiva e operacional sobre a qualidade do produto, organizada
 * por funcionalidades.</p>
 *
 * <h2>Resumo das responsabilidades</h2>
 * <ul>
 *   <li>Agrupar Test Cases, Test Results e Defects por suite (funcionalidade);</li>
 *   <li>Calcular métricas fundamentais como: casos totais, executados,
 *       passed, failed, aborted e não executados;</li>
 *   <li>Relacionar Defects às funcionalidades através dos casos de teste;</li>
 *   <li>Calcular percentuais de execução e incidência de bugs por funcionalidade;</li>
 *   <li>Gerar um JSONArray estruturado para consumo pelo ReportGenerator;</li>
 * </ul>
 *
 * <p>Este serviço não realiza cálculos complexos ou análises temporais; ele
 * atua exclusivamente no nível de agregação de dados por funcionalidade.</p>
 */
public class FunctionalSummaryService {

    /**
     * Prepara o resumo funcional para todos os projetos incluídos no consolidated.
     *
     * <p>Fluxo para cada projeto:</p>
     * <ol>
     *   <li>Extrair arrays seguros de cases, results, defects e suites;</li>
     *   <li>Gerar o resumo por funcionalidade com {@link #generateFunctionalSummary};</li>
     *   <li>Embalá-lo como JSONObject contendo o array "functionalities";</li>
     *   <li>Retornar um mapa projectKey → summary.</li>
     * </ol>
     *
     * @param consolidatedData Mapa contendo consolidated.json por projeto.
     * @return Map projectKey → JSONObject com estatísticas por funcionalidade.
     */
    public Map<String, JSONObject> prepareData(Map<String, JSONObject> consolidatedData) {
        Map<String, JSONObject> summaries = new HashMap<>();

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            // Arrays tolerantes a nomes alternativos
            JSONArray cases = safeArray(projectData, "case", "cases");
            JSONArray results = safeArray(projectData, "result", "results");
            JSONArray defects = safeArray(projectData, "defect", "defects");
            JSONArray suites = safeArray(projectData, "suite", "suites");

            // Geração do resumo funcional
            JSONArray functionalities = generateFunctionalSummary(
                projectKey, cases, results, defects, suites);

            JSONObject summary = new JSONObject();
            summary.put("functionalities", functionalities);
            summaries.put(projectKey, summary);

            LoggerUtils.step("✅ Resumo gerado para o projeto: " + projectKey);
        }

        return summaries;
    }

    /**
     * Gera o resumo consolidado de funcionalidades para um projeto.
     *
     * <p>Este método executa cinco etapas principais:</p>
     *
     * <ol>
     *   <li><b>Inicialização:</b> cria uma linha-base para cada suite conhecida.</li>
     *   <li><b>Contagem de casos:</b> associa cases às suites e contabiliza totalCases.</li>
     *   <li><b>Contagem de execuções:</b> agrupa resultados e incrementa métricas
     *   como passed, failed, executed, aborted e notExecuted.</li>
     *   <li><b>Contagem de defects:</b> relaciona bugs às suites via case_id
     *   e categoriza por status (open, closed, ignored).</li>
     *   <li><b>Cálculo de percentuais:</b> executados/total e bugs/executados.</li>
     * </ol>
     *
     * @param projectKey Identificador do projeto no consolidated.
     * @param cases      Array de casos de teste.
     * @param results    Array de resultados de execução.
     * @param defects    Array de defeitos associados.
     * @param suites     Array de suítes de funcionalidades.
     * @return JSONArray estruturado contendo uma linha por funcionalidade.
     */
    private JSONArray generateFunctionalSummary(
        String projectKey,
        JSONArray cases,
        JSONArray results,
        JSONArray defects,
        JSONArray suites
    ) {
        Map<String, JSONObject> summaryMap = new HashMap<>();

        // ============================================================
        // 1) Inicializa linhas base por suite
        // ============================================================
        for (int i = 0; i < suites.length(); i++) {
            JSONObject suite = suites.getJSONObject(i);
            String suiteName = suite.optString("title", "Sem Nome");
            summaryMap.put(suiteName, baseRow(suiteName));
        }

        // ============================================================
        // 2) Contabiliza casos de teste
        // ============================================================
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String suiteName = findSuiteName(c.optInt("suite_id"), suites);

            JSONObject s = summaryMap.getOrDefault(suiteName, baseRow(suiteName));
            s.put("totalCases", s.getInt("totalCases") + 1);
            summaryMap.put(suiteName, s);
        }

        // ============================================================
        // 3) Contabiliza resultados de execução
        // ============================================================
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

        // ============================================================
        // 4) Contabiliza defects por suite
        // ============================================================
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

        // ============================================================
        // 5) Calcula percentuais e produz array final
        // ============================================================
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
     * Cria uma linha-base do resumo funcional, inicializando todos os campos
     * numéricos com zero e definindo o nome da suíte.
     *
     * @param suiteName Nome da funcionalidade.
     * @return JSONObject contendo os campos padrão.
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
     * Localiza o nome da suíte com base em seu identificador.
     *
     * @param suiteId ID da suite.
     * @param suites  Lista de suites presentes no consolidated.
     * @return Nome da suite ou "Sem Nome" caso não exista.
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
     * Busca e retorna o caso de teste associado ao ID fornecido.
     *
     * @param cases Array de casos.
     * @param id    Identificador do caso.
     * @return JSONObject do caso ou objeto vazio como fallback.
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
     * Extrai um JSONArray de forma tolerante, aceitando tanto arrays quanto objetos.
     *
     * @param source JSON original.
     * @param keys   Chaves que podem conter o array.
     * @return JSONArray correspondente ou vazio se nenhuma chave válida existir.
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
