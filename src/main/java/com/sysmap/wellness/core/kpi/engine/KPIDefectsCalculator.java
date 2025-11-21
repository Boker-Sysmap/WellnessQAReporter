package com.sysmap.wellness.core.kpi.engine;

import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cálculo do KPI de defeitos (defectsCount).
 */
public class KPIDefectsCalculator {

    public KPIData calculate(JSONObject consolidated, String project, String releaseId) {
        JSONArray defects = consolidated.optJSONArray("defect");
        if (defects == null) return null;

        int count = defects.length();

        return KPIData.of(
            "defectsCount",
            "Total de Defeitos",
            count,
            project,
            releaseId
        );
    }
}
