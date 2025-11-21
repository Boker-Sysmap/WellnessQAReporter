package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.engine.KPIEngine;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.service.model.ReleaseContext;
import com.sysmap.wellness.report.service.model.RunStats;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * KPI de Resultados da Release (distribuição de status):
 *
 * <ul>
 *   <li>releaseResults.passedPct  → % de casos executados com status PASSED;</li>
 *   <li>releaseResults.failedPct  → % de casos executados com status FAILED;</li>
 *   <li>releaseResults.blockedPct → % de casos executados com status BLOCKED;</li>
 *   <li>releaseResults.skippedPct → % de casos executados com status SKIPPED;</li>
 *   <li>releaseResults.retestPct  → % de casos executados com status RETEST.</li>
 * </ul>
 *
 * <p>Regras de cálculo:</p>
 * <ul>
 *   <li>Base = {@link RunStats#getExecutedCases()} (somente casos executados);</li>
 *   <li>Percentuais calculados como (status / executados) * 100;</li>
 *   <li>Sem casas decimais: o valor é arredondado para o inteiro mais próximo;</li>
 *   <li>Se executedCases &lt;= 0, todos os percentuais são 0 e um aviso é logado.</li>
 * </ul>
 */
public class KPIReleaseResultsService implements KPIEngine.ReleaseKPIService {

    // Chave "família" usada apenas para identificação no log de erro do KPIEngine
    private static final String KPI_FAMILY_KEY = "releaseResults";

    // Chaves técnicas individuais dos KPIs
    public static final String KPI_KEY_PASSED  = "releaseResults.passedPct";
    public static final String KPI_KEY_FAILED  = "releaseResults.failedPct";
    public static final String KPI_KEY_BLOCKED = "releaseResults.blockedPct";
    public static final String KPI_KEY_SKIPPED = "releaseResults.skippedPct";
    public static final String KPI_KEY_RETEST  = "releaseResults.retestPct";

    @Override
    public String getKpiKey() {
        // Usado apenas para identificação em logs de erro no KPIEngine
        return KPI_FAMILY_KEY;
    }

    @Override
    public List<KPIData> calculateForRelease(
        String project,
        ReleaseContext ctx,
        List<JSONObject> plans,
        List<JSONObject> runs,
        RunStats stats
    ) {
        Objects.requireNonNull(ctx, "ReleaseContext não pode ser nulo");

        String releaseId = ctx.getOfficialId();

        LoggerUtils.step("📊 Calculando KPI: Resultados da Release — projeto=" +
            project + ", release=" + releaseId);

        int executed = stats != null ? stats.getExecutedCases() : 0;
        int passed   = stats != null ? stats.getPassed()          : 0;
        int failed   = stats != null ? stats.getFailed()          : 0;
        int blocked  = stats != null ? stats.getBlocked()         : 0;
        int skipped  = stats != null ? stats.getSkipped()         : 0;
        int retest   = stats != null ? stats.getRetest()          : 0;

        if (executed <= 0) {
            LoggerUtils.warn("⚠ Nenhum caso executado para projeto=" + project +
                ", release=" + releaseId +
                ". Resultados da Release serão todos 0%.");
        }

        double passedPct  = percent(passed, executed);
        double failedPct  = percent(failed, executed);
        double blockedPct = percent(blocked, executed);
        double skippedPct = percent(skipped, executed);
        double retestPct  = percent(retest, executed);

        LoggerUtils.success(String.format(
            Locale.US,
            "📌 Resultados da Release %s — Executados=%d | P=%d%% F=%d%% B=%d%% S=%d%% R=%d%%",
            releaseId,
            executed,
            (int) passedPct,
            (int) failedPct,
            (int) blockedPct,
            (int) skippedPct,
            (int) retestPct
        ));

        List<KPIData> out = new ArrayList<>();

        // PASSED
        out.add(
            new KPIBuilder(KPI_KEY_PASSED, "Release - Passed (%)")
                .fromReleaseContext(ctx)
                .project(project)
                .value(passedPct)
                .formattedValue(formatPercentNoDecimals(passedPct))
                .percent(true)
                .description("Percentual de casos PASSED na release " + releaseId)
                .build()
        );

        // FAILED
        out.add(
            new KPIBuilder(KPI_KEY_FAILED, "Release - Failed (%)")
                .fromReleaseContext(ctx)
                .project(project)
                .value(failedPct)
                .formattedValue(formatPercentNoDecimals(failedPct))
                .percent(true)
                .description("Percentual de casos FAILED na release " + releaseId)
                .build()
        );

        // BLOCKED
        out.add(
            new KPIBuilder(KPI_KEY_BLOCKED, "Release - Blocked (%)")
                .fromReleaseContext(ctx)
                .project(project)
                .value(blockedPct)
                .formattedValue(formatPercentNoDecimals(blockedPct))
                .percent(true)
                .description("Percentual de casos BLOCKED na release " + releaseId)
                .build()
        );

        // SKIPPED
        out.add(
            new KPIBuilder(KPI_KEY_SKIPPED, "Release - Skipped (%)")
                .fromReleaseContext(ctx)
                .project(project)
                .value(skippedPct)
                .formattedValue(formatPercentNoDecimals(skippedPct))
                .percent(true)
                .description("Percentual de casos SKIPPED na release " + releaseId)
                .build()
        );

        // RETEST
        out.add(
            new KPIBuilder(KPI_KEY_RETEST, "Release - Retest (%)")
                .fromReleaseContext(ctx)
                .project(project)
                .value(retestPct)
                .formattedValue(formatPercentNoDecimals(retestPct))
                .percent(true)
                .description("Percentual de casos RETEST na release " + releaseId)
                .build()
        );

        return out;
    }

    /**
     * Calcula o percentual inteiro (0-100) a partir de um valor e do total executado.
     *
     * @param value     quantidade no status específico (passed, failed, etc.).
     * @param executed  total de casos executados na release.
     * @return percentual arredondado para o inteiro mais próximo.
     */
    private double percent(int value, int executed) {
        if (executed <= 0 || value <= 0) {
            return 0.0;
        }
        double raw = (value * 100.0) / executed;
        // sem casas decimais → inteiro mais próximo
        return Math.max(0.0, Math.round(raw));
    }

    /**
     * Formata um percentual sem casas decimais, ex.: 75 → "75%".
     */
    private String formatPercentNoDecimals(double pct) {
        // pct já vem arredondado em percent(), mas garantimos aqui
        long v = Math.round(Math.max(0.0, pct));
        return v + "%";
    }
}
