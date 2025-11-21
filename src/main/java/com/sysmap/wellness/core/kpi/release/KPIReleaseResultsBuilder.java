package com.sysmap.wellness.core.kpi.release;

import com.sysmap.wellness.report.service.model.KPIData;

import java.util.List;

public class KPIReleaseResultsBuilder {

    public static final String KPI_PREFIX = "releaseResults";

    public List<KPIData> build(
        KPIReleaseResultsCalculator.ResultPct pct,
        String project,
        String releaseId
    ) {
        return List.of(
            create("passedPct",  pct.passedPct,  project, releaseId),
            create("failedPct",  pct.failedPct,  project, releaseId),
            create("blockedPct", pct.blockedPct, project, releaseId),
            create("retestPct",  pct.retestPct,  project, releaseId)
        );
    }

    private KPIData create(String suffix, double value, String project, String releaseId) {

        String key = KPI_PREFIX + "." + suffix;

        String label =
            "passedPct".equals(suffix)   ? "Passed (%)" :
                "failedPct".equals(suffix)   ? "Failed (%)" :
                    "blockedPct".equals(suffix)  ? "Blocked (%)" :
                        "Retest (%)";

        return KPIData.of(
            key,
            label,
            value,
            project,
            releaseId
        );
    }
}
