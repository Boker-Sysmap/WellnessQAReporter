package com.sysmap.wellness.report.service.model;

import java.util.List;

public class KPIResult {
    public final String releaseId;
    public final List<KPIData> kpis;

    public KPIResult(String releaseId, List<KPIData> kpis) {
        this.releaseId = releaseId;
        this.kpis = kpis;
    }
}
