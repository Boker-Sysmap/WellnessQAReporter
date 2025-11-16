package com.sysmap.wellness.utils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utilitário de acesso rápido a coleções de entidades Qase dentro do
 * objeto consolidado de um projeto no WellnessQAReporter.
 *
 * <p>
 * Após a etapa de consolidação executada por {@code DataConsolidator},
 * cada projeto passa a possuir um objeto JSON estruturado desta forma:
 * </p>
 *
 * <pre>{@code
 * {
 *   "case": [...],
 *   "suite": [...],
 *   "defect": [...],
 *   "run": [...],
 *   "result": [...],        // opcional nos modos RUN-BASED
 *
 *   "run_results": {        // formato RUN-BASED
 *       "6":   [...],       // results do run 6
 *       "16":  [...],       // results do run 16
 *       ...
 *   }
 * }
 * }</pre>
 *
 * <p>
 * Os métodos desta classe fornecem acesso direto às listas principais,
 * reduzindo boilerplate e evitando chamadas repetidas a {@code optJSONArray()}.
 * </p>
 *
 * <p>
 * É especialmente útil para serviços como:
 * <ul>
 *     <li>FunctionalSummaryService</li>
 *     <li>DefectsAnalyticalService</li>
 *     <li>RunBasedEngine</li>
 *     <li>ScopeKPIService</li>
 * </ul>
 * </p>
 *
 * <h3>Exemplo básico:</h3>
 * <pre>{@code
 * JSONObject fully = consolidated.get("FULLY");
 * JSONArray cases   = QaseDataUtils.getCases(fully);
 * JSONArray defects = QaseDataUtils.getDefects(fully);
 * }</pre>
 *
 * <h3>Exemplo usando run_results:</h3>
 * <pre>{@code
 * JSONObject fully = consolidated.get("FULLY");
 * JSONObject runResults = QaseDataUtils.getRunResultsMap(fully);
 * JSONArray resultsRun16 = runResults.optJSONArray("16");
 * }</pre>
 *
 * @author
 * @version 1.1
 */
public class QaseDataUtils {

    /**
     * Retorna o array de casos de teste ({@code "case"}).
     *
     * @param projectData JSON consolidado do projeto.
     * @return JSONArray de casos de teste, ou {@code null} se ausente.
     */
    public static JSONArray getCases(JSONObject projectData) {
        return projectData.optJSONArray("case");
    }

    /**
     * Retorna o array global de resultados ({@code "result"}) do projeto.
     *
     * <p>
     * Observação: no modo RUN-BASED, a coleta principal fica organizada
     * em {@code "run_results"}; este array pode estar vazio ou não existir.
     * </p>
     *
     * @param projectData JSON consolidado do projeto.
     * @return JSONArray com resultados, ou {@code null}.
     */
    public static JSONArray getResults(JSONObject projectData) {
        return projectData.optJSONArray("result");
    }

    /**
     * Retorna o array de defeitos ({@code "defect"}) do projeto.
     *
     * @param projectData JSON consolidado do projeto.
     * @return JSONArray contendo os defeitos, ou {@code null}.
     */
    public static JSONArray getDefects(JSONObject projectData) {
        return projectData.optJSONArray("defect");
    }

    /**
     * Retorna o array de usuários ({@code "user"}) associados ao projeto.
     *
     * @param projectData JSON consolidado do projeto.
     * @return JSONArray de usuários, ou {@code null}.
     */
    public static JSONArray getUsers(JSONObject projectData) {
        return projectData.optJSONArray("user");
    }

    /**
     * Retorna o array de execuções ({@code "run"}) do projeto.
     *
     * @param projectData JSON consolidado do projeto.
     * @return JSONArray contendo execuções, ou {@code null}.
     */
    public static JSONArray getRuns(JSONObject projectData) {
        return projectData.optJSONArray("run");
    }

    // =============================================================
    // 🔹 UTILITÁRIO ESPECÍFICO (modo RUN-BASED)
    // =============================================================

    /**
     * Retorna o mapa {@code "run_results"} que contém os resultados
     * organizados por run_id.
     *
     * <p>
     * Formato retornado:
     * </p>
     *
     * <pre>{@code
     * {
     *   "6":  [...],
     *   "16": [...],
     *   "20": [...],
     *   ...
     * }
     * }</pre>
     *
     * <p>
     * Esse formato é fundamental para serviços como:
     * </p>
     * <ul>
     *     <li>DefectsAnalyticalService → mapear case_id → suite → funcionalidade</li>
     *     <li>RunBasedExecutionCurve → curva por execução</li>
     *     <li>KPIEngine (multi-release) → localizar origem dos resultados</li>
     * </ul>
     *
     * @param projectData JSON consolidado do projeto.
     * @return {@code JSONObject} contendo run_results, ou um JSON vazio caso não exista.
     */
    public static JSONObject getRunResultsMap(JSONObject projectData) {
        JSONObject map = projectData.optJSONObject("run_results");
        return map != null ? map : new JSONObject();
    }
}
