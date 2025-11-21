package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.model.ReleaseContext;
import com.sysmap.wellness.report.service.model.ReleaseSummaryRow;
import com.sysmap.wellness.report.service.model.RunStats;
import org.json.JSONObject;

import java.util.List;

/**
 * Camada de negócio responsável por consolidar uma release inteira
 * em uma linha única de resumo (ReleaseSummaryRow).
 *
 * NÃO calcula KPIs independentes.
 * Ele consolida:
 *  - Escopo planejado
 *  - Executados
 *  - Cobertura
 *  - % Passed / Failed / Blocked / Skipped / Retest
 *
 * Usado pelo KPIEngine antes do ReportGenerator.
 */
public class ReleaseSummaryAggregator {

    /**
     * Produz uma linha consolidada da release.
     *
     * @param project Nome do projeto (FULLY, CHUBB...)
     * @param ctx ReleaseContext contendo version_environment
     * @param plans Test Plans da release
     * @param stats Estatísticas agregadas das runs (RunStats)
     */
    public ReleaseSummaryRow aggregate(
        String project,
        ReleaseContext ctx,
        List<JSONObject> plans,
        RunStats stats
    ) {

        int plannedScope = computePlannedScope(plans);

        int executed = stats.getExecutedCases();
        int passed   = stats.getPassed();
        int failed   = stats.getFailed();
        int blocked  = stats.getBlocked();
        int skipped  = stats.getSkipped();
        int retest   = stats.getRetest();

        // evitar divisão por zero
        double base = executed > 0 ? executed : 1.0;

        double passedPct  = round((passed  * 100.0) / base);
        double failedPct  = round((failed  * 100.0) / base);
        double blockedPct = round((blocked * 100.0) / base);
        double skippedPct = round((skipped * 100.0) / base);
        double retestPct  = round((retest  * 100.0) / base);

        // cobertura da release
        double coveragePct = 0.0;
        if (plannedScope > 0) {
            coveragePct = round((executed * 100.0) / plannedScope);
        }

        return new ReleaseSummaryRow(
            project,
            ctx.getOfficialId(),
            plannedScope,
            coveragePct,
            passedPct,
            failedPct,
            blockedPct,
            skippedPct,
            retestPct
        );
    }

    /**
     * Soma o campo "cases_count" dos Test Plans da release.
     */
    private int computePlannedScope(List<JSONObject> plans) {
        if (plans == null || plans.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (JSONObject p : plans) {
            if (p == null) continue;
            total += p.optInt("cases_count", 0);
        }
        return Math.max(0, total);
    }

    /**
     * Arredonda para zero casas decimais (modelo do seu relatório).
     */
    private double round(double v) {
        return Math.round(v);
    }
}
