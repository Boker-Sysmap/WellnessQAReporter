package com.sysmap.wellness.core.kpi.engine;

import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula taxas de execução:
 *  - passedRate
 *  - failedRate
 *  - blockedRate
 *  - skippedRate
 *  - unexecutedRate
 *  - totalExecuted (absoluto)
 */
public class KPIExecutionRatesCalculator {

    public List<KPIData> calculate(JSONObject consolidated, String project, String releaseId) {
        List<KPIData> list = new ArrayList<>();

        JSONArray runs = consolidated.optJSONArray("run");
        if (runs == null || runs.isEmpty()) return list;

        int passed = 0, failed = 0, blocked = 0, skipped = 0, untested = 0;

        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONObject stats = run.optJSONObject("stats");

            if (stats != null) {
                passed   += stats.optInt("passed", 0);
                failed   += stats.optInt("failed", 0);
                blocked  += stats.optInt("blocked", 0);
                skipped  += stats.optInt("skipped", 0);
                untested += stats.optInt("untested", 0);
                continue;
            }

            passed   += run.optInt("passed", 0);
            failed   += run.optInt("failed", 0);
            blocked  += run.optInt("blocked", 0);
            skipped  += run.optInt("skipped", 0);
            untested += run.optInt("untested", 0);
        }

        int total = passed + failed + blocked + skipped + untested;
        if (total == 0) total = 1;

        list.add(percentKpi("passedRate",    "Taxa de Pass",      project, releaseId, passed,   total));
        list.add(percentKpi("failedRate",    "Taxa de Falha",     project, releaseId, failed,   total));
        list.add(percentKpi("blockedRate",   "Bloqueados",        project, releaseId, blocked,  total));
        list.add(percentKpi("skippedRate",   "Ignorados",         project, releaseId, skipped,  total));
        list.add(percentKpi("unexecutedRate","Não executados",    project, releaseId, untested, total));

        list.add(KPIData.of(
            "totalExecuted",
            "Total Executado",
            passed + failed + blocked + skipped,
            project,
            releaseId
        ));

        return list;
    }

    private KPIData percentKpi(
        String key, String name, String project, String releaseId, int part, int total
    ) {
        double value = (part * 100.0 / total);
        return new KPIData(
            key,
            name,
            value,
            String.format("%.0f%%", value),
            "→",
            name,
            true,
            project,
            releaseId
        );
    }
}
