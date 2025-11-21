package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.engine.KPIEngine;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.service.model.ReleaseContext;
import com.sysmap.wellness.report.service.model.RunStats;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

import java.util.*;

/**
 * KPI de Cobertura da Release:
 *
 * - plannedScope  → total de test cases planejados na release (soma de cases_count dos plans);
 * - releaseCoverage → % de casos executados / escopo planejado.
 *
 * Regras:
 * - NÃO ignorar releases com plannedScope = 0; isso é considerado anomalia de dados/config;
 * - Executados = passed + failed + blocked + skipped + retest (já calculado em RunStats);
 * - Sempre retorna 2 KPIs: plannedScope e releaseCoverage.
 */
public class KPIReleaseCoverageService implements KPIEngine.ReleaseKPIService {

    public static final String KPI_KEY_SCOPE = "plannedScope";
    public static final String KPI_KEY_COVERAGE = "releaseCoverage";

    @Override
    public String getKpiKey() {
        // chave principal deste serviço (usada apenas como identificação no log de erro)
        return KPI_KEY_COVERAGE;
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
        LoggerUtils.step("📊 Calculando KPI: Cobertura da Release — projeto=" +
            project + ", release=" + releaseId);

        int plannedScope = computePlannedScope(plans);
        int executed = stats != null ? stats.getExecutedCases() : 0;

        if (plannedScope <= 0) {
            // NÃO filtra a release; só avisa.
            LoggerUtils.warn("⚠ plannedScope=0 para projeto=" + project +
                ", release=" + releaseId +
                ". Isso não deveria ocorrer em produção. Verifique os Test Plans/cases no Qase.");
        }

        double coveragePct = 0.0;
        if (plannedScope > 0 && executed > 0) {
            coveragePct = (executed * 100.0) / plannedScope;
        }

        LoggerUtils.success("📌 Cobertura calculada: " + String.format(Locale.US, "%.2f", coveragePct) +
            "% (" + executed + "/" + plannedScope + ") para release " + releaseId);

        List<KPIData> out = new ArrayList<>();

        // KPI 1: Escopo planejado
        KPIData kpiScope = new KPIBuilder(KPI_KEY_SCOPE, "Escopo planejado")
            .fromReleaseContext(ctx)
            .project(project)
            .value(plannedScope)
            .formattedValue(String.valueOf(plannedScope))
            .description("Total de casos planejados para a release " + releaseId)
            .percent(false)
            .build();

        out.add(kpiScope);

        // KPI 2: Cobertura da Release
        String formatted = String.format(Locale.US, "%.2f%%", coveragePct);

        KPIData kpiCoverage = new KPIBuilder(KPI_KEY_COVERAGE, "Cobertura da Release (%)")
            .fromReleaseContext(ctx)
            .project(project)
            .value(coveragePct)
            .formattedValue(formatted)
            .percent(true)
            .description("Percentual de casos executados na release " + releaseId)
            .build();

        out.add(kpiCoverage);

        return out;
    }

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
}
