package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KPIEngine {

    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("([A-Z]+-[0-9]{4}-[0-9]{2}-R[0-9]{2})");

    private final KPIHistoryService historyService = new KPIHistoryService();
    private final KPIService kpiService = new KPIService();

    /**
     * Calcula KPIs para TODOS os projetos e TODAS as releases encontradas.
     */
    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidatedData,
        String fallbackRelease) {

        Map<String, List<KPIData>> result = new LinkedHashMap<>();

        for (String project : consolidatedData.keySet()) {

            JSONObject consolidated = consolidatedData.get(project);

            List<String> releases = detectAllReleaseIds(consolidated, project, fallbackRelease);

            LoggerUtils.info("→ Releases detectadas para " + project + ": " + releases);

            List<KPIData> allKPIs = new ArrayList<>();

            for (String release : releases) {

                LoggerUtils.info("⚙ Calculando KPIs da release " + release);

                // Filtra o consolidated para a release atual
                JSONObject filtered = filterConsolidatedByRelease(consolidated, release);

                // KPIs calculados pelo serviço original
                List<KPIData> baseKPIs = kpiService.calculateKPIs(filtered, project);

                // Converte todos para KPIs "amarrados" à release
                List<KPIData> releaseKPIs = new ArrayList<>();

                for (KPIData k : baseKPIs) {
                    releaseKPIs.add(k.withGroup(release));
                }

                // Salva o histórico da release
                historyService.saveAll(project, release, releaseKPIs);

                allKPIs.addAll(releaseKPIs);
            }

            result.put(project, allKPIs);
        }

        return result;
    }

    /**
     * Filtra o JSON consolidated para conter apenas planos da release.
     */
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        JSONObject filtered = new JSONObject(full.toString()); // deep clone seguro

        JSONArray originalPlans = full.optJSONArray("plan");
        JSONArray filteredPlans = new JSONArray();

        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");
                if (title.contains(releaseId)) {
                    filteredPlans.put(p);
                }
            }
        }

        filtered.put("plan", filteredPlans);
        return filtered;
    }

    /**
     * Detecta TODAS as releases presentes nos títulos dos Test Plans.
     */
    private List<String> detectAllReleaseIds(JSONObject consolidated,
                                             String project,
                                             String fallback) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            return Collections.singletonList(fallback);
        }

        Set<String> releases = new TreeSet<>(Comparator.reverseOrder());

        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            String title = plan.optString("title", null);
            String releaseId = extractReleaseId(title);

            if (releaseId != null) releases.add(releaseId);
        }

        if (releases.isEmpty()) releases.add(fallback);

        return new ArrayList<>(releases);
    }

    /**
     * Extrai o formato FULLYREPO-2025-02-R01 do título.
     */
    private String extractReleaseId(String title) {
        if (title == null) return null;

        Matcher matcher = RELEASE_PATTERN.matcher(title);
        if (matcher.find()) return matcher.group(1);

        return null;
    }
}
