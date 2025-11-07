package com.sysmap.wellness.report.service;

import com.sysmap.wellness.util.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Serviço responsável por consolidar e normalizar os dados
 * do endpoint 'defect' (ou 'defects') para o relatório
 * "Gestão de Defeitos - Analítico".
 *
 * Ele atua como uma camada intermediária entre o DataConsolidator
 * e o DefectAnalyticalReportSheet, garantindo que a estrutura
 * dos dados esteja sempre consistente.
 */
public class DefectAnalyticalService {

    /**
     * Extrai e prepara os dados de defeitos de cada projeto.
     *
     * @param consolidatedData Mapa de projetos → JSON contendo endpoints (case, suite, result, defect etc)
     * @return Mapa de projetos → JSONArray com defeitos normalizados
     */
    public Map<String, JSONArray> prepareData(Map<String, JSONObject> consolidatedData) {
        Map<String, JSONArray> projectDefects = new HashMap<>();

        LoggerUtils.step("🔎 Iniciando consolidação de defeitos...");

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            JSONArray defectsArray = safeArray(projectData, "defects", "defect");
            JSONArray usersArray = safeArray(projectData, "users", "user");
            JSONArray suitesArray = safeArray(projectData, "suites", "suite");
            JSONArray runsArray = safeArray(projectData, "runs", "run");

            LoggerUtils.step("📦 Projeto " + projectKey +
                    ": defects=" + defectsArray.length() +
                    ", users=" + usersArray.length() +
                    ", suites=" + suitesArray.length() +
                    ", runs=" + runsArray.length());

            // Normaliza e enriquece os dados dos defeitos
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

                // Enriquecimento (nome do autor, nome da suíte, etc.)
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

    /** Busca nome do usuário pelo ID. */
    private String findUserName(JSONArray users, int userId) {
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            if (user.optInt("id") == userId) {
                return user.optString("full_name", user.optString("name", "Desconhecido"));
            }
        }
        return "Desconhecido";
    }

    /** Busca nome da suíte pelo ID. */
    private String findSuiteName(JSONArray suites, int suiteId) {
        for (int i = 0; i < suites.length(); i++) {
            JSONObject suite = suites.getJSONObject(i);
            if (suite.optInt("id") == suiteId) {
                return suite.optString("title", "Sem nome");
            }
        }
        return "Sem nome";
    }

    /** Busca nome do run pelo ID. */
    private String findRunName(JSONArray runs, int runId) {
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.getJSONObject(i);
            if (run.optInt("id") == runId) {
                return run.optString("title", "Sem nome");
            }
        }
        return "Sem nome";
    }

    /** Retorna um JSONArray seguro mesmo que o campo não exista. */
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
