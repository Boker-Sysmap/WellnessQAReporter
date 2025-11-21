package com.sysmap.wellness.core.kpi.engine;

import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cálculo do KPI de Cobertura da Release (releaseCoverage).
 */
public class KPICoverageCalculator {

    public KPIData calculate(JSONObject consolidated, String project, String releaseId) {

        JSONArray runs = consolidated.optJSONArray("run");

        if (runs == null || runs.isEmpty()) {
            return KPIData.of(
                "releaseCoverage",
                "Cobertura da Release",
                0,
                project,
                releaseId
            );
        }

        int executed = 0;
        int total = 0;

        for (int i = 0; i < runs.length(); i++) {

            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONObject stats = run.optJSONObject("stats");

            if (stats != null) {

                int sTotal   = stats.optInt("total", 0);
                int sPassed  = stats.optInt("passed", 0);
                int sFailed  = stats.optInt("failed", 0);
                int sBlocked = stats.optInt("blocked", 0);
                int sSkipped = stats.optInt("skipped", 0);

                total    += sTotal;
                executed += (sPassed + sFailed + sBlocked + sSkipped);
                continue;
            }

            // Fallback para formato antigo (campos diretos no run)
            int p = run.optInt("passed", 0);
            int f = run.optInt("failed", 0);
            int b = run.optInt("blocked", 0);
            int s = run.optInt("skipped", 0);
            int u = run.optInt("untested", 0);

            executed += (p + f + b + s);
            total    += (p + f + b + s + u);
        }

        double percent = (total == 0) ? 0 : (executed * 100.0 / total);
        String formatted = String.format("%.0f%%", percent);

        return new KPIData(
            "releaseCoverage",
            "Cobertura da Release",
            percent,
            formatted,
            "→",
            "Percentual de casos executados ao menos uma vez",
            true,
            project,
            releaseId
        );
    }
}
