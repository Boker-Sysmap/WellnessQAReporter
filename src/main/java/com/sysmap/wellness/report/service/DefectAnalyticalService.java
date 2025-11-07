package com.sysmap.wellness.report.service;

import com.sysmap.wellness.util.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço responsável por consolidar, normalizar e enriquecer os dados do endpoint
 * <b>"defects"</b> provenientes da API Qase.
 *
 * <p>Esta classe atua como camada intermediária entre:
 * <ul>
 *   <li>{@link com.sysmap.wellness.service.DataConsolidator} — que fornece os dados brutos consolidados</li>
 *   <li>{@link com.sysmap.wellness.report.sheet.DefectAnalyticalReportSheet} — que gera o relatório Excel</li>
 * </ul>
 *
 * <p>O objetivo principal é garantir que todos os dados de defeitos estejam estruturados
 * de maneira uniforme e enriquecidos com informações adicionais (como autor, suíte e execução)
 * antes de serem enviados para o relatório analítico.</p>
 *
 * <h2>Funções principais:</h2>
 * <ol>
 *   <li>Normalizar os registros de defeitos de diferentes endpoints.</li>
 *   <li>Enriquecer os dados com informações cruzadas (usuários, suítes e execuções).</li>
 *   <li>Gerar uma estrutura JSON simplificada e padronizada para consumo pelo relatório final.</li>
 * </ol>
 *
 * <p>Logs detalhados são gerados a cada etapa do processamento por meio do {@link LoggerUtils},
 * permitindo rastreabilidade e auditoria durante a execução.</p>
 *
 * @author Roberto
 * @version 1.1
 * @since 1.0
 */
public class DefectAnalyticalService {

    /**
     * Prepara os dados de defeitos para todos os projetos disponíveis no conjunto consolidado.
     *
     * <p>Este método percorre todos os projetos e extrai do objeto {@link JSONObject}
     * os endpoints relevantes (defects, users, suites e runs). Em seguida, normaliza
     * e enriquece os dados, gerando um {@link JSONArray} uniforme com as informações
     * essenciais para o relatório analítico.</p>
     *
     * @param consolidatedData Mapa de projetos e seus respectivos {@link JSONObject}s contendo os endpoints consolidados.
     *                         Exemplo de estrutura: <code>projectKey → { "defects": [...], "users": [...], "suites": [...] }</code>
     * @return Mapa de projetos com seus respectivos {@link JSONArray}s de defeitos normalizados.
     *         Exemplo: <code>projectKey → [ { "id": 1, "title": "...", "author_name": "..." } ]</code>
     */
    public Map<String, JSONArray> prepareData(Map<String, JSONObject> consolidatedData) {
        Map<String, JSONArray> projectDefects = new HashMap<>();

        LoggerUtils.step("🔎 Iniciando consolidação de defeitos...");

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            // Carrega arrays seguros de cada endpoint
            JSONArray defectsArray = safeArray(projectData, "defects", "defect");
            JSONArray usersArray = safeArray(projectData, "users", "user");
            JSONArray suitesArray = safeArray(projectData, "suites", "suite");
            JSONArray runsArray = safeArray(projectData, "runs", "run");

            LoggerUtils.step("📦 Projeto " + projectKey +
                    ": defects=" + defectsArray.length() +
                    ", users=" + usersArray.length() +
                    ", suites=" + suitesArray.length() +
                    ", runs=" + runsArray.length());

            // Normalização e enriquecimento
            JSONArray normalizedDefects = new JSONArray();
            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                JSONObject enriched = new JSONObject();

                enriched.put("id", defect.opt("id"));
                enriched.put("title", defect.opt("title"));
                enriched.put("status", defect.opt("status"));
                enriched.put("severity", defect.opt("severity"));
                enriched.put("priority", defect.opt("priority"));
                enriched.put("suite_id", defect.opt("suite_id"));
                enriched.put("run_id", defect.opt("run_id"));
                enriched.put("author_id", defect.opt("user_id"));
                enriched.put("created_at", defect.opt("created"));
                enriched.put("updated_at", defect.opt("updated"));
                enriched.put("resolved_at", defect.opt("resolved"));

                // Enriquecimento adicional
                if (defect.has("user_id")) {
                    enriched.put("author_name", findUserName(usersArray, defect.getInt("user_id")));
                }
                if (defect.has("suite_id")) {
                    enriched.put("suite_name", findSuiteName(suitesArray, defect.getInt("suite_id")));
                }
                if (defect.has("run_id")) {
                    enriched.put("run_name", findRunName(runsArray, defect.getInt("run_id")));
                }

                normalizedDefects.put(enriched);
            }

            projectDefects.put(projectKey, normalizedDefects);
            LoggerUtils.success("✅ " + normalizedDefects.length() + " defeitos preparados para " + projectKey);
        }

        return projectDefects;
    }

    /**
     * Busca o nome completo de um usuário com base em seu ID.
     *
     * @param users  Lista de usuários disponíveis.
     * @param userId ID do usuário desejado.
     * @return Nome completo do usuário, ou "Desconhecido" se não encontrado.
     */
    private String findUserName(JSONArray users, int userId) {
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            if (user.optInt("id") == userId) {
                return user.optString("full_name", user.optString("name", "Desconhecido"));
            }
        }
        return "Desconhecido";
    }

    /**
     * Busca o nome da suíte de testes associada ao ID informado.
     *
     * @param suites  Lista de suítes disponíveis.
     * @param suiteId ID da suíte desejada.
     * @return Título da suíte, ou "Sem nome" se não encontrado.
     */
    private String findSuiteName(JSONArray suites, int suiteId) {
        for (int i = 0; i < suites.length(); i++) {
            JSONObject suite = suites.getJSONObject(i);
            if (suite.optInt("id") == suiteId) {
                return suite.optString("title", "Sem nome");
            }
        }
        return "Sem nome";
    }

    /**
     * Busca o nome da execução de testes (run) associada ao ID informado.
     *
     * @param runs  Lista de execuções disponíveis.
     * @param runId ID da execução desejada.
     * @return Título da execução, ou "Sem nome" se não encontrado.
     */
    private String findRunName(JSONArray runs, int runId) {
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.getJSONObject(i);
            if (run.optInt("id") == runId) {
                return run.optString("title", "Sem nome");
            }
        }
        return "Sem nome";
    }

    /**
     * Retorna um {@link JSONArray} seguro, mesmo que a chave especificada não exista
     * ou contenha apenas um objeto singular.
     *
     * <p>Este método evita exceções ao acessar campos inexistentes e converte
     * automaticamente um {@link JSONObject} em um {@link JSONArray} com um único elemento,
     * garantindo consistência de estrutura.</p>
     *
     * @param source Objeto JSON de origem.
     * @param keys   Lista de possíveis chaves a serem verificadas (ordem de prioridade).
     * @return {@link JSONArray} correspondente, ou vazio se nenhuma chave for encontrada.
     */
    private JSONArray safeArray(JSONObject source, String... keys) {
        for (String key : keys) {
            if (source.has(key)) {
                Object val = source.get(key);
                if (val instanceof JSONArray) return (JSONArray) val;
                if (val instanceof JSONObject) {
                    JSONArray single = new JSONArray();
                    single.put((JSONObject) val);
                    return single;
                }
            }
        }
        return new JSONArray();
    }
}
