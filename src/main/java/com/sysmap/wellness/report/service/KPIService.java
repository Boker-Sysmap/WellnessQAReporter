package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
import com.sysmap.wellness.report.service.kpi.KPIReleaseResultsService;   // ⭐ NOVO KPI
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.ReleaseUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável pelo cálculo dos KPIs base de uma release,
 * antes da etapa multi-release realizada no KPIEngine.
 *
 * <p>Todos os KPIs retornados por este serviço devem utilizar
 * identificadores técnicos (key) fixos, permitindo consistência
 * histórica e integridade nos dashboards/workbooks.</p>
 *
 * <p>O nome amigável é exibido para o usuário e pode variar,
 * mas a key nunca muda.</p>
 */
public class KPIService {

    private final ScopeKPIService scopeKpi = new ScopeKPIService();

    // ⭐ Novo KPI
    private final KPIReleaseResultsService releaseResultsKpi = new KPIReleaseResultsService();

    /**
     * Calcula todos os KPIs base para um consolidated já filtrado por release.
     *
     * @param consolidated consolidated.json filtrado pela release.
     * @param project Nome do projeto.
     * @return Lista de KPIs da release.
     */
    public List<KPIData> calculateKPIs(JSONObject consolidated, String project) {

        List<KPIData> list = new ArrayList<>();

        LoggerUtils.section("📊 Calculando KPIs BASE para " + project);

        // --------------------------------------------------------------------
        // 1. Extrair release real do consolidated filtrado
        // --------------------------------------------------------------------
        String releaseId = extractReleaseFromFiltered(consolidated);
        LoggerUtils.info("🔎 Release identificada no consolidated filtrado: " + releaseId);

        // --------------------------------------------------------------------
        // 2. Escopo Planejado (plannedScope)
        // --------------------------------------------------------------------
        KPIData plannedScope = scopeKpi.calculate(consolidated, project, releaseId);
        plannedScope = KPIData.of(
            "plannedScope",
            "Escopo planejado",
            plannedScope.getValue(),
            project,
            releaseId
        );
        list.add(plannedScope);

        // --------------------------------------------------------------------
        // 3. Cobertura da Release (%)
        // --------------------------------------------------------------------
        KPIData releaseCoverage = calculateReleaseCoverage(consolidated, project, releaseId);
        list.add(releaseCoverage);

        // --------------------------------------------------------------------
        // 3.1 ⭐ NOVO KPI: Resultados da Release (Passed / Failed / Blocked / Retest)
        // --------------------------------------------------------------------
        JSONArray runs = consolidated.optJSONArray("run");

        // Agora o cálculo usa TODOS os runs da release
        list.addAll(releaseResultsKpi.calculate(runs, project, releaseId));

        // --------------------------------------------------------------------
        // 4. Taxas de execução (pass/fail/blocked/skipped/unexecuted)
        // --------------------------------------------------------------------
        list.addAll(calculateExecutionRates(consolidated, project, releaseId));

        // --------------------------------------------------------------------
        // 5. Defeitos
        // --------------------------------------------------------------------
        KPIData defects = calculateDefects(consolidated, project, releaseId);
        if (defects != null) list.add(defects);

        return list;
    }

    // =====================================================================
    // KPI: RELEASE COVERAGE (CORRIGIDO)
    // =====================================================================
    private KPIData calculateReleaseCoverage(
        JSONObject consolidated,
        String project,
        String releaseId
    ) {
        JSONArray runs = consolidated.optJSONArray("run");

        if (runs == null || runs.isEmpty()) {
            return KPIData.of(
                "releaseCoverage",
                "Cobertura da Release",
                0,
                project,
                releaseId
            );
        }

        int executed = 0;
        int total = 0;

        for (int i = 0; i < runs.length(); i++) {

            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONObject stats = run.optJSONObject("stats");

            if (stats != null) {

                int sTotal   = stats.optInt("total", 0);
                int sPassed  = stats.optInt("passed", 0);
                int sFailed  = stats.optInt("failed", 0);
                int sBlocked = stats.optInt("blocked", 0);
                int sSkipped = stats.optInt("skipped", 0);

                total += sTotal;
                executed += (sPassed + sFailed + sBlocked + sSkipped);
                continue;
            }

            // Fallback
            executed += run.optInt("passed", 0)
                + run.optInt("failed", 0)
                + run.optInt("blocked", 0)
                + run.optInt("skipped", 0);

            total += run.optInt("passed", 0)
                + run.optInt("failed", 0)
                + run.optInt("blocked", 0)
                + run.optInt("skipped", 0)
                + run.optInt("untested", 0);
        }

        double percent = (total == 0) ? 0 : (executed * 100.0 / total);
        String formatted = String.format("%.0f%%", percent);

        return new KPIData(
            "releaseCoverage",
            "Cobertura da Release",
            percent,
            formatted,
            "→",
            "Percentual de casos executados ao menos uma vez",
            true,
            project,
            releaseId
        );
    }

    // =====================================================================
    // KPI: Execution Rates
    // =====================================================================
    private List<KPIData> calculateExecutionRates(
        JSONObject consolidated,
        String project,
        String releaseId
    ) {
        List<KPIData> list = new ArrayList<>();

        JSONArray runs = consolidated.optJSONArray("run");
        if (runs == null || runs.isEmpty()) return list;

        int passed = 0, failed = 0, blocked = 0, skipped = 0, untested = 0;

        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONObject stats = run.optJSONObject("stats");

            if (stats != null) {
                passed  += stats.optInt("passed", 0);
                failed  += stats.optInt("failed", 0);
                blocked += stats.optInt("blocked", 0);
                skipped += stats.optInt("skipped", 0);
                untested += stats.optInt("untested", 0);
                continue;
            }

            passed  += run.optInt("passed", 0);
            failed  += run.optInt("failed", 0);
            blocked += run.optInt("blocked", 0);
            skipped += run.optInt("skipped", 0);
            untested += run.optInt("untested", 0);
        }

        int total = passed + failed + blocked + skipped + untested;
        if (total == 0) total = 1;

        list.add(percentKpi("passedRate",   "Taxa de Pass",     project, releaseId, passed, total));
        list.add(percentKpi("failedRate",   "Taxa de Falha",    project, releaseId, failed, total));
        list.add(percentKpi("blockedRate",  "Bloqueados",       project, releaseId, blocked, total));
        list.add(percentKpi("skippedRate",  "Ignorados",        project, releaseId, skipped, total));
        list.add(percentKpi("unexecutedRate","Não executados", project, releaseId, untested, total));

        list.add(KPIData.of(
            "totalExecuted",
            "Total Executado",
            passed + failed + blocked + skipped,
            project,
            releaseId
        ));

        return list;
    }

    private KPIData percentKpi(
        String key, String name, String project, String releaseId, int part, int total
    ) {
        double value = (part * 100.0 / total);
        return new KPIData(
            key,
            name,
            value,
            String.format("%.0f%%", value),
            "→",
            name,
            true,
            project,
            releaseId
        );
    }

    // =====================================================================
    // KPI: Defeitos
    // =====================================================================
    private KPIData calculateDefects(JSONObject consolidated, String project, String releaseId) {
        JSONArray defects = consolidated.optJSONArray("defect");
        if (defects == null) return null;

        int count = defects.length();

        return KPIData.of(
            "defectsCount",
            "Total de Defeitos",
            count,
            project,
            releaseId
        );
    }

    // =====================================================================
    // AUXILIAR: Extrair release real do consolidated já filtrado
    // =====================================================================
    private String extractReleaseFromFiltered(JSONObject consolidated) {

        JSONArray plans = consolidated.optJSONArray("plan");

        if (plans != null && !plans.isEmpty()) {
            JSONObject p = plans.optJSONObject(0);
            if (p != null) {
                String title = p.optString("title", "");
                String detected = ReleaseUtils.extractReleaseIdFromTitle(title);

                if (detected != null) {
                    return detected;
                }
            }
        }

        return "UNKNOWN-RELEASE";
    }
}
