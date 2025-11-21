package com.sysmap.wellness.core.kpi.release;

public class KPIReleaseResultsCalculator {

    public static class ResultPct {
        public double passedPct;
        public double failedPct;
        public double blockedPct;
        public double retestPct;
    }

    public ResultPct calculate(KPIReleaseResultsAggregator.Result raw) {

        long total = (long) raw.passed + raw.failed + raw.blocked + raw.retest;

        ResultPct pct = new ResultPct();

        if (total == 0) {
            pct.passedPct = 0;
            pct.failedPct = 0;
            pct.blockedPct = 0;
            pct.retestPct = 0;
            return pct;
        }

        pct.passedPct  = round(raw.passed  * 100.0 / total);
        pct.failedPct  = round(raw.failed  * 100.0 / total);
        pct.blockedPct = round(raw.blocked * 100.0 / total);
        pct.retestPct  = round(raw.retest  * 100.0 / total);

        return pct;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
