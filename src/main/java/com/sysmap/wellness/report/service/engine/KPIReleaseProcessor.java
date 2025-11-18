package com.sysmap.wellness.report.service.engine;

import com.sysmap.wellness.report.service.kpi.engine.KPIService;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * Responsável por disparar o cálculo de KPIs para uma release específica.
 */
public class KPIReleaseProcessor {

    private final KPIService kpiService = new KPIService();

    public List<KPIData> processRelease(
        String project,
        String releaseId,
        JSONObject filteredConsolidated
    ) {
        if (filteredConsolidated == null) {
            return Collections.emptyList();
        }

        // O KPIService já produz KPIs com project + releaseId corretos.
        return kpiService.calculateKPIs(filteredConsolidated, project);
    }
}
