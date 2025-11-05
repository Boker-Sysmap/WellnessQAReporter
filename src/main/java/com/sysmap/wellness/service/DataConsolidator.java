package com.sysmap.wellness.service;

import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Consolida os dados brutos (cases, results, defects etc.)
 * e agora enriquece os resultados com base nos hashes dos defects.
 */
public class DataConsolidator {

    private final QaseClient qaseClient = new QaseClient();

    public Map<String, JSONObject> consolidateAll(Map<String, Map<String, JSONArray>> allData) {
        LoggerUtils.step("🧩 Iniciando consolidação dos dados...");
        Map<String, JSONObject> consolidatedProjects = new LinkedHashMap<>();

        for (var entry : allData.entrySet()) {
            String projectCode = entry.getKey();
            Map<String, JSONArray> projectEndpoints = entry.getValue();
            JSONObject consolidated = consolidateProject(projectCode, projectEndpoints);
            consolidatedProjects.put(projectCode, consolidated);
        }

        LoggerUtils.success("✅ Consolidação concluída para " + consolidatedProjects.size() + " projetos.");
        return consolidatedProjects;
    }

    private JSONObject consolidateProject(String projectCode, Map<String, JSONArray> endpoints) {
        LoggerUtils.step("🔧 Consolidando projeto: " + projectCode);

        JSONObject consolidated = new JSONObject();

        consolidated.put("cases", endpoints.getOrDefault("case", new JSONArray()));
        consolidated.put("results", endpoints.getOrDefault("result", new JSONArray()));
        consolidated.put("defects", endpoints.getOrDefault("defect", new JSONArray()));
        consolidated.put("suites", endpoints.getOrDefault("suite", new JSONArray()));

        if (endpoints.containsKey("milestone")) {
            consolidated.put("milestones", endpoints.get("milestone"));
        }

        enrichResultsFromDefects(projectCode, consolidated);

        int totalCases = consolidated.getJSONArray("cases").length();
        int totalResults = consolidated.getJSONArray("results").length();
        int totalDefects = consolidated.getJSONArray("defects").length();

        JSONObject stats = new JSONObject()
                .put("casesCount", totalCases)
                .put("resultsCount", totalResults)
                .put("defectsCount", totalDefects)
                .put("milestonesCount", consolidated.has("milestones")
                        ? consolidated.getJSONArray("milestones").length() : 0);

        consolidated.put("stats", stats);

        LoggerUtils.success(String.format("📊 %s consolidado: %d casos, %d resultados, %d defeitos",
                projectCode, totalCases, totalResults, totalDefects));

        MetricsCollector.increment("projectsConsolidated");
        MetricsCollector.incrementBy("casesTotal", totalCases);
        MetricsCollector.incrementBy("resultsTotal", totalResults);
        MetricsCollector.incrementBy("defectsTotal", totalDefects);

        return consolidated;
    }

    /**
     * 🔍 Enriquecer resultados com base nos hashes contidos nos defects.
     */
    private void enrichResultsFromDefects(String projectCode, JSONObject consolidated) {
        JSONArray defects = consolidated.optJSONArray("defects");
        JSONArray results = consolidated.optJSONArray("results");
        if (defects == null || defects.isEmpty()) return;

        Set<String> existingHashes = new HashSet<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            if (r.has("hash")) existingHashes.add(r.getString("hash"));
        }

        for (int i = 0; i < defects.length(); i++) {
            JSONObject defect = defects.getJSONObject(i);
            if (!defect.has("results")) continue;

            JSONArray defectResults = defect.optJSONArray("results");
            if (defectResults == null || defectResults.isEmpty()) continue;

            for (int j = 0; j < defectResults.length(); j++) {
                String hash = defectResults.getString(j);
                if (hash == null || existingHashes.contains(hash)) continue;

                JSONObject result = qaseClient.fetchResultByHash(projectCode, hash);
                if (result != null) {
                    results.put(result);
                    existingHashes.add(hash);
                    LoggerUtils.step("➕ Result hash " + hash + " adicionado via defect.");
                }
            }
        }
    }
}
