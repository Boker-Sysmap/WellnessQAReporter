package com.sysmap.wellness.report.service.kpi.release;

import org.json.JSONArray;
import org.json.JSONObject;

public class KPIReleaseResultsAggregator {

    public static class Result {
        public int passed;
        public int failed;
        public int blocked;
        public int retest;
    }

    /**
     * Soma todos os resultados dos runs.
     * Aplica regra: skipped → blocked.
     */
    public Result aggregate(JSONArray runs) {
        Result r = new Result();

        if (runs == null || runs.isEmpty()) {
            return r;
        }

        for (int i = 0; i < runs.length(); i++) {

            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONObject stats = run.optJSONObject("stats");

            if (stats != null) {
                r.passed  += stats.optInt("passed", 0);
                r.failed  += stats.optInt("failed", 0);
                r.blocked += stats.optInt("blocked", 0) + stats.optInt("skipped", 0);
                r.retest  += stats.optInt("retest", 0);
                continue;
            }

            // fallback antigo
            r.passed  += run.optInt("passed", 0);
            r.failed  += run.optInt("failed", 0);
            r.blocked += run.optInt("blocked", 0) + run.optInt("skipped", 0);
            r.retest  += run.optInt("retest", 0);
        }

        return r;
    }
}
