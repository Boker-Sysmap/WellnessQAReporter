package com.sysmap.wellness.utils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Classe utilitária para facilitar o acesso a conjuntos de dados (arrays JSON)
 * retornados pela API Qase, dentro da estrutura consolidada de um projeto.
 *
 * <p>Esta classe fornece métodos auxiliares para obter rapidamente
 * listas específicas de entidades — como casos de teste, resultados,
 * defeitos, usuários e execuções (runs) — de um {@link JSONObject}
 * representando os dados de um projeto.</p>
 *
 * <p>Os métodos utilizam {@link JSONObject#optJSONArray(String)},
 * o que significa que retornam {@code null} caso a chave não exista
 * ou o valor não seja um {@link JSONArray}, evitando exceções.</p>
 *
 * <h3>Exemplo de uso:</h3>
 * <pre>{@code
 * JSONObject projectData = consolidatedData.get("MYPROJECT");
 * JSONArray cases = QaseDataUtils.getCases(projectData);
 * JSONArray defects = QaseDataUtils.getDefects(projectData);
 * }</pre>
 *
 * <p>Esses utilitários são especialmente úteis para manter o código
 * de extração de dados limpo e consistente em serviços de relatório.</p>
 *
 * @author
 * @version 1.0
 */
public class QaseDataUtils {

    /**
     * Retorna o array de casos de teste ({@code "case"}) do projeto.
     *
     * @param projectData Objeto JSON consolidado do projeto.
     * @return {@link JSONArray} contendo os casos de teste,
     *         ou {@code null} se a chave não existir.
     */
    public static JSONArray getCases(JSONObject projectData) {
        return projectData.optJSONArray("case");
    }

    /**
     * Retorna o array de resultados de execução ({@code "result"}) do projeto.
     *
     * @param projectData Objeto JSON consolidado do projeto.
     * @return {@link JSONArray} contendo os resultados,
     *         ou {@code null} se a chave não existir.
     */
    public static JSONArray getResults(JSONObject projectData) {
        return projectData.optJSONArray("result");
    }

    /**
     * Retorna o array de defeitos ({@code "defect"}) do projeto.
     *
     * @param projectData Objeto JSON consolidado do projeto.
     * @return {@link JSONArray} contendo os defeitos,
     *         ou {@code null} se a chave não existir.
     */
    public static JSONArray getDefects(JSONObject projectData) {
        return projectData.optJSONArray("defect");
    }

    /**
     * Retorna o array de usuários ({@code "user"}) do projeto.
     *
     * @param projectData Objeto JSON consolidado do projeto.
     * @return {@link JSONArray} contendo os usuários,
     *         ou {@code null} se a chave não existir.
     */
    public static JSONArray getUsers(JSONObject projectData) {
        return projectData.optJSONArray("user");
    }

    /**
     * Retorna o array de execuções ({@code "run"}) do projeto.
     *
     * @param projectData Objeto JSON consolidado do projeto.
     * @return {@link JSONArray} contendo as execuções (runs),
     *         ou {@code null} se a chave não existir.
     */
    public static JSONArray getRuns(JSONObject projectData) {
        return projectData.optJSONArray("run");
    }
}
