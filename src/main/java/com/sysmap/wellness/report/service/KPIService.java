package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
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
 * Todos os KPIs retornados por este serviço utilizam keys técnicas fixas
 * para garantir consistência histórica e integridade dos dashboards.
 */
public class KPIService {

    private final ScopeKPIService scopeKpi = new ScopeKPIService();

    /**
     * Calcula todos os KPIs base para um consolidated já filtrado pela release.
     */
    public List<KPIData> calculateKPIs(JSONObject consolidated, String project) {

        List<KPIData> list = new ArrayList<>();

        LoggerUtils.section("📊 Calculando KPIs BASE para " + project);

        // --------------------------------------------------------------------
        // 1. Identificar a release do consolidated filtrado (CORRIGIDO)
        // --------------------------------------------------------------------
        String releaseId = extractReleaseFromFiltered(consolidated);

        // --------------------------------------------------------------------
        // 2. Escopo Planejado
        // --------------------------------------------------------------------
        KPIData plannedScope = scopeKpi.calculate(consolidated, project, releaseId);

        // Garantimos que a key seja sempre a técnica correta
        plannedScope = KPIData.of(
            "plannedScope",
            "Escopo planejado",
            plannedScope.getValue(),
            project,
            releaseId
        );
        list.add(plannedScope);

        // --------------------------------------------------------------------
        // 3. Cobertura da Release
        // --------------------------------------------------------------------
        KPIData releaseCoverage = calculateReleaseCoverage(consolidated, project, releaseId);
        list.add(releaseCoverage);

        // --------------------------------------------------------------------
        // 4. Taxas de execução (pass/fail/blocked/unexecuted)
        // --------------------------------------------------------------------
        list.addAll(calculateExecutionRates(consolidated, project, releaseId));

        // --------------------------------------------------------------------
        // 5. Defeitos (se existirem)
        // --------------------------------------------------------------------
        KPIData defects = calculateDefects(consolidated, project, releaseId);
        if (defects != null) list.add(defects);

        return list;
    }


    // =====================================================================
    // KPI: RELEASE COVERAGE  (usa stats.total / stats.passed / stats.failed)
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

            // Formato novo: bloco stats
            JSONObject stats = run.optJSONObject("stats");
            if (stats != null) {

                int sTotal = stats.optInt("total", 0);
                int sPassed = stats.optInt("passed", 0);
                int sFailed = stats.optInt("failed", 0);

                total += sTotal;
                executed += (sPassed + sFailed);

                continue; // skip fallback
            }

            // Fallback: formato antigo
            int p = run.optInt("passed", 0);
            int f = run.optInt("failed", 0);
            int b = run.optInt("blocked", 0);
            int u = run.optInt("untested", 0);

            executed += (p + f + b);
            total += (p + f + b + u);
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
    // KPIs de Execução
    // =====================================================================
    private List<KPIData> calculateExecutionRates(
        JSONObject consolidated,
        String project,
        String releaseId
    ) {
        List<KPIData> list = new ArrayList<>();

        JSONArray runs = consolidated.optJSONArray("run");
        if (runs == null || runs.isEmpty()) return list;

        int passed = 0, failed = 0, blocked = 0, untested = 0;
        int total = 0;

        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            // Tenta ler do bloco stats (novo)
            JSONObject stats = run.optJSONObject("stats");
            if (stats != null) {

                passed   += stats.optInt("passed", 0);
                failed   += stats.optInt("failed", 0);
                blocked  += stats.optInt("blocked", 0);
                untested += stats.optInt("untested", 0);

                total += stats.optInt("total", 0);
                continue;
            }

            // Formato antigo
            passed   += run.optInt("passed", 0);
            failed   += run.optInt("failed", 0);
            blocked  += run.optInt("blocked", 0);
            untested += run.optInt("untested", 0);
        }

        if (total == 0) total = 1;

        list.add(percentKpi("passedRate",     "Taxa de Pass",      project, releaseId, passed, total));
        list.add(percentKpi("failedRate",     "Taxa de Falha",     project, releaseId, failed, total));
        list.add(percentKpi("blockedRate",    "Taxa de Bloqueio",  project, releaseId, blocked, total));
        list.add(percentKpi("unexecutedRate", "Não executados",    project, releaseId, untested, total));

        list.add(KPIData.of(
            "totalExecuted",
            "Total Executado",
            passed + failed + blocked,
            project,
            releaseId
        ));

        return list;
    }

    private KPIData percentKpi(
        String key, String name,
        String project, String releaseId,
        int part, int total
    ) {
        double value = (part * 100.0 / total);
        String formatted = String.format("%.0f%%", value);

        return new KPIData(
            key,
            name,
            value,
            formatted,
            "→",
            name,
            true,
            project,
            releaseId
        );
    }


    // =====================================================================
    // KPIs de Defeitos
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
    // AUXILIAR — Identificar release real do consolidated já filtrado
    // =====================================================================
    private String extractReleaseFromFiltered(JSONObject consolidated) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            return "UNKNOWN-RELEASE";
        }

        JSONObject p = plans.optJSONObject(0);
        if (p == null) {
            return "UNKNOWN-RELEASE";
        }

        String title = p.optString("title", "").trim();
        if (title.isEmpty()) {
            return "UNKNOWN-RELEASE";
        }

        // ✔ Usa ReleaseUtils para extrair SOMENTE o ID da release
        String extracted = ReleaseUtils.extractReleaseIdFromTitle(title);
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }

        return "UNKNOWN-RELEASE";
    }
}
