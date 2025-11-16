package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.ReleaseUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class KPIEngine {

    private final KPIHistoryService historyService = new KPIHistoryService();
    private final KPIService kpiService = new KPIService();

    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidatedData,
        String fallbackRelease
    ) {

        Map<String, List<KPIData>> result = new LinkedHashMap<>();

        for (String project : consolidatedData.keySet()) {

            LoggerUtils.info("============================================================");
            LoggerUtils.info("📌 PROCESSANDO PROJETO: " + project);
            LoggerUtils.info("============================================================");

            JSONObject consolidated = consolidatedData.get(project);

            // ============================================================
            // Detectar releases do projeto (ReleaseUtils aplicado)
            // ============================================================
            List<String> detectedReleases =
                detectAllReleaseIds(consolidated, fallbackRelease);

            LoggerUtils.info("🗂 Releases detectadas: " + detectedReleases);

            // ============================================================
            // Histórico
            // ============================================================
            List<KPIHistoryRecord> history = historyService.getAllHistory(project);

            Set<String> releasesWithHistory =
                history.stream().map(KPIHistoryRecord::getReleaseName).collect(Collectors.toSet());

            String newestReleaseInHistory = getNewestRelease(history);
            boolean hasHistory = !releasesWithHistory.isEmpty();

            List<KPIData> allKPIs = new ArrayList<>();

            // ============================================================
            // Processar cada release
            // ============================================================
            for (String release : detectedReleases) {

                boolean exists = releasesWithHistory.contains(release);
                boolean isNewest = hasHistory && release.equals(newestReleaseInHistory);

                boolean shouldProcess =
                    !hasHistory || !exists || isNewest;

                if (!shouldProcess) {
                    LoggerUtils.info("⛔ Release congelada: " + release);
                    continue;
                }

                // ============================================================
                // Filtrar consolidated pela release (com ReleaseUtils)
                // ============================================================
                JSONObject filtered = filterConsolidatedByRelease(consolidated, release);

                // ============================================================
                // KPIs base
                // ============================================================
                List<KPIData> baseKPIs = kpiService.calculateKPIs(filtered, project);

                // Adiciona releaseId ao KPIData
                List<KPIData> releaseKPIs = baseKPIs.stream()
                    .map(k -> k.withGroup(release))
                    .collect(Collectors.toList());

                historyService.saveAll(project, release, releaseKPIs);

                allKPIs.addAll(releaseKPIs);
            }

            result.put(project, allKPIs);
        }

        return result;
    }

    // ============================================================
    // Filtrar consolidated por release (APERFEIÇOADO)
    // Agora Plans e Runs usam ReleaseUtils corretamente
    // ============================================================
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        JSONObject copy = new JSONObject(full.toString()); // Deep clone seguro

        JSONArray plans = full.optJSONArray("plan");
        JSONArray runs = full.optJSONArray("run");

        JSONArray filteredPlans = new JSONArray();
        JSONArray filteredRuns = new JSONArray();

        // ---------------------------------------------
        // PLAN: mesmo mecanismo do ScopeKPIService
        // ---------------------------------------------
        if (plans != null) {
            for (int i = 0; i < plans.length(); i++) {
                JSONObject p = plans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");

                if (ReleaseUtils.isPlanFromRelease(title, releaseId)) {
                    filteredPlans.put(p);
                }
            }
        }

        // ---------------------------------------------
        // RUN: usa extração precisa do releaseId
        // (corrige o problema do releaseCoverage=0)
        // ---------------------------------------------
        if (runs != null) {
            for (int i = 0; i < runs.length(); i++) {
                JSONObject r = runs.optJSONObject(i);
                if (r == null) continue;

                String title = r.optString("title", "");
                String extracted = ReleaseUtils.extractReleaseIdFromTitle(title);

                if (extracted != null &&
                    ReleaseUtils.normalize(extracted).equals(ReleaseUtils.normalize(releaseId))) {
                    filteredRuns.put(r);
                }
            }
        }

        copy.put("plan", filteredPlans);
        copy.put("run", filteredRuns);

        return copy;
    }

    // ============================================================
    // Detectar todas as releases usando ReleaseUtils
    // ============================================================
    private List<String> detectAllReleaseIds(JSONObject consolidated, String fallback) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) return List.of(fallback);

        Set<String> releases = new TreeSet<>(Comparator.reverseOrder());
        String fallbackNorm = ReleaseUtils.normalize(fallback);

        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;

            String title = p.optString("title", "");

            String detected = ReleaseUtils.extractReleaseIdFromTitle(title);
            if (detected != null) {
                releases.add(detected);
                continue;
            }

            // fallback compatibility
            if (ReleaseUtils.normalize(title).contains(fallbackNorm)) {
                releases.add(fallback);
            }
        }

        if (releases.isEmpty()) releases.add(fallback);

        return new ArrayList<>(releases);
    }

    // ============================================================
    // Release mais recente
    // ============================================================
    private String getNewestRelease(List<KPIHistoryRecord> history) {
        if (history == null || history.isEmpty()) return null;

        return history.stream()
            .map(KPIHistoryRecord::getReleaseName)
            .sorted()
            .reduce((a, b) -> b)
            .orElse(null);
    }
}
