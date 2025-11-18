package com.sysmap.wellness.report.service.engine;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Filtra o consolidated.json para manter apenas:
 *  - Plans da release alvo
 *  - Runs da release alvo
 *
 * Demais estruturas são preservadas (cases, suites, defects, run_results, etc.).
 */
public class KPIReleaseFilter {

    public JSONObject filter(JSONObject full, String releaseId) {
        if (full == null || releaseId == null) return full;

        JSONObject copy = new JSONObject(full.toString());

        JSONArray originalPlans = full.optJSONArray("plan");
        JSONArray originalRuns  = full.optJSONArray("run");

        JSONArray filteredPlans = new JSONArray();
        JSONArray filteredRuns  = new JSONArray();

        // PLAN – releaseIdentifier OU title.contains(releaseId) (compatibilidade)
        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title   = p.optString("title", "");
                String planRel = p.optString("releaseIdentifier", null);

                if ((planRel != null && planRel.equals(releaseId))
                    || (title != null && title.contains(releaseId))) {
                    filteredPlans.put(p);
                }
            }
        }

        // RUN – sempre via releaseIdentifier
        if (originalRuns != null) {
            for (int i = 0; i < originalRuns.length(); i++) {
                JSONObject r = originalRuns.optJSONObject(i);
                if (r == null) continue;

                String runRel = r.optString("releaseIdentifier", null);
                if (runRel != null && runRel.equals(releaseId)) {
                    filteredRuns.put(r);
                }
            }
        }

        copy.put("plan", filteredPlans);
        copy.put("run", filteredRuns);

        return copy;
    }
}
