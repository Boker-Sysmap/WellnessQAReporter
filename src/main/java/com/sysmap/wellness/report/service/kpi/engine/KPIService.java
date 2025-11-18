package com.sysmap.wellness.report.service.kpi.engine;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
import com.sysmap.wellness.report.service.kpi.KPIReleaseResultsService;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * KPIService – Fachada modularizada.
 *
 * Orquestra módulos especializados:
 *  • Escopo planejado
 *  • Cobertura da Release
 *  • Resultados (Passed/Failed/Blocked/Retest)
 *  • Taxas de execução
 *  • Defeitos
 */
public class KPIService {

    private final ScopeKPIService scopeKpi = new ScopeKPIService();
    private final KPIReleaseResultsService releaseResultsKpi = new KPIReleaseResultsService();

    private final KPIReleaseExtractor releaseExtractor = new KPIReleaseExtractor();
    private final KPICoverageCalculator coverageCalculator = new KPICoverageCalculator();
    private final KPIExecutionRatesCalculator executionRatesCalculator = new KPIExecutionRatesCalculator();
    private final KPIDefectsCalculator defectsCalculator = new KPIDefectsCalculator();

    /**
     * Calcula todos os KPIs base para um consolidated já filtrado por release.
     *
     * @param consolidated consolidated.json filtrado pela release
     * @param project      código do projeto
     */
    public List<KPIData> calculateKPIs(JSONObject consolidated, String project) {

        List<KPIData> list = new ArrayList<>();

        LoggerUtils.section("📊 Calculando KPIs BASE para " + project);

        // 1) Detectar release real
        String releaseId = releaseExtractor.extractRelease(consolidated);
        LoggerUtils.info("🔎 Release identificada: " + releaseId);

        // 2) Escopo planejado (ScopeKPIService já retorna key/name corretos)
        KPIData plannedScope = scopeKpi.calculate(consolidated, project, releaseId);
        list.add(plannedScope);

        // 3) Cobertura da release
        list.add(coverageCalculator.calculate(consolidated, project, releaseId));

        // 4) Resultados (passed/failed/blocked/retest) usando TODOS os runs
        JSONArray runs = consolidated.optJSONArray("run");
        list.addAll(releaseResultsKpi.calculate(runs, project, releaseId));

        // 5) Taxas de execução
        list.addAll(executionRatesCalculator.calculate(consolidated, project, releaseId));

        // 6) Defeitos
        KPIData defects = defectsCalculator.calculate(consolidated, project, releaseId);
        if (defects != null) list.add(defects);

        return list;
    }
}
