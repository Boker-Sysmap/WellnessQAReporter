package com.sysmap.wellness.report.service.kpi;

import com.sysmap.wellness.report.service.kpi.release.KPIReleaseResultsAggregator;
import com.sysmap.wellness.report.service.kpi.release.KPIReleaseResultsBuilder;
import com.sysmap.wellness.report.service.kpi.release.KPIReleaseResultsCalculator;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;

import java.util.List;

public class KPIReleaseResultsService {

    private final KPIReleaseResultsAggregator aggregator = new KPIReleaseResultsAggregator();
    private final KPIReleaseResultsCalculator calculator = new KPIReleaseResultsCalculator();
    private final KPIReleaseResultsBuilder builder = new KPIReleaseResultsBuilder();

    public List<KPIData> calculate(JSONArray runs, String project, String releaseId) {

        if (runs == null || runs.isEmpty()) {
            LoggerUtils.warn("KPIReleaseResultsService: nenhum run encontrado para a release " + releaseId);
            return builder.build(
                calculator.calculate(new KPIReleaseResultsAggregator.Result()),
                project,
                releaseId
            );
        }

        KPIReleaseResultsAggregator.Result raw = aggregator.aggregate(runs);
        KPIReleaseResultsCalculator.ResultPct pct = calculator.calculate(raw);

        return builder.build(pct, project, releaseId);
    }
}
