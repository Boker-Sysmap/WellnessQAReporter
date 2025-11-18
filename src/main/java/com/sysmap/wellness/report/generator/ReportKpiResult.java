package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.report.service.model.KPIData;

import java.util.List;
import java.util.Map;

/**
 * Estrutura de retorno do {@link ReportKpiProcessor}, contendo:
 * <ul>
 *   <li>KPIs por projeto;</li>
 *   <li>Release principal por projeto.</li>
 * </ul>
 */
public class ReportKpiResult {

    private final Map<String, List<KPIData>> kpisByProject;
    private final Map<String, String> releaseByProject;

    public ReportKpiResult(
        Map<String, List<KPIData>> kpisByProject,
        Map<String, String> releaseByProject
    ) {
        this.kpisByProject = kpisByProject;
        this.releaseByProject = releaseByProject;
    }

    public Map<String, List<KPIData>> getKpisByProject() {
        return kpisByProject;
    }

    public Map<String, String> getReleaseByProject() {
        return releaseByProject;
    }
}
