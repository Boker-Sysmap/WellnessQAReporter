package com.sysmap.wellness.report.service.engine;

import com.sysmap.wellness.core.kpi.service.*;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.service.model.ReleaseContext;
import com.sysmap.wellness.report.service.model.ReleaseSummaryRow;
import com.sysmap.wellness.report.service.model.RunStats;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Engine central de KPIs por release.
 *
 * Agora também agrega um ReleaseSummaryRow (linha consolidada por release),
 * para uso direto no Painel Consolidado baseado em 1 linha por release.
 */
public class KPIEngine {

    private final ReleaseMatcher matcher;
    private final RunStatsAggregator aggregator = new RunStatsAggregator();
    private final HistoryWriter history = new HistoryWriter();
    private final ReleaseSummaryAggregator summaryAggregator = new ReleaseSummaryAggregator();

    private final List<ReleaseKPIService> services;

    public KPIEngine(ReleaseMatcher.ReleaseNameParser parser,
                     List<ReleaseKPIService> customServices) {

        this.matcher = new ReleaseMatcher(parser);

        List<ReleaseKPIService> base = new ArrayList<>();
        base.add(new KPIReleaseCoverageService());
        base.add(new KPIReleaseResultsService());

        if (customServices != null) {
            base.addAll(customServices);
        }

        this.services = base;

        LoggerUtils.info(
            "KPIEngine inicializado com " + this.services.size() + " serviços de KPI por release."
        );
    }

    // ============================================================
    // CONTRATO
    // ============================================================

    public interface ReleaseKPIService {
        String getKpiKey();

        List<KPIData> calculateForRelease(
            String project,
            ReleaseContext ctx,
            List<JSONObject> plans,
            List<JSONObject> runs,
            RunStats stats
        );
    }

    // ============================================================
    // MODELO DE RESULTADO
    // ============================================================

    public static class KPIEngineResult {
        public final Map<String, List<KPIData>> kpisByRelease;
        public final Map<String, ReleaseSummaryRow> summaryByRelease;
        public final JSONArray updatedHistory;

        KPIEngineResult(Map<String, List<KPIData>> kpisByRelease,
                        Map<String, ReleaseSummaryRow> summaryByRelease,
                        JSONArray updatedHistory) {

            this.kpisByRelease = kpisByRelease;
            this.summaryByRelease = summaryByRelease;
            this.updatedHistory = updatedHistory;
        }
    }

    // ============================================================
    // API PRINCIPAL
    // ============================================================

    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidated
    ) {
        Map<String, List<KPIData>> out = new LinkedHashMap<>();

        if (consolidated == null || consolidated.isEmpty()) {
            return out;
        }

        for (String project : consolidated.keySet()) {

            JSONObject obj = consolidated.get(project);
            if (obj == null) continue;

            JSONArray plans = obj.optJSONArray("plan");
            JSONArray runs = obj.optJSONArray("run");
            JSONArray historyArr = obj.optJSONArray("kpiHistory");

            KPIEngineResult res = calculateForProject(project, plans, runs, historyArr);

            // KPIs brutos mantêm compatibilidade
            out.put(project, flatten(res.kpisByRelease));

            // Summaries convertidos para JSON e anexados ao consolidated
            obj.put("releaseSummaries", convertSummaries(res.summaryByRelease));
            obj.put("kpiHistory", res.updatedHistory);
        }

        return out;
    }

    // ============================================================
    // CONVERTE SUMMARIES PARA JSON
    // ============================================================

    private JSONArray convertSummaries(Map<String, ReleaseSummaryRow> map) {
        JSONArray arr = new JSONArray();

        for (ReleaseSummaryRow r : map.values()) {

            JSONObject o = new JSONObject()
                .put("project", r.getProject())
                .put("releaseId", r.getReleaseId())
                .put("plannedScope", r.getPlannedScope())
                // coverage já vem arredondada pelo aggregator
                .put("coveragePct", r.getCoveragePct())
                .put("passedPct", r.getPassedPct())
                .put("failedPct", r.getFailedPct())
                .put("blockedPct", r.getBlockedPct())
                .put("skippedPct", r.getSkippedPct())
                .put("retestPct", r.getRetestPct());

            arr.put(o);
        }

        return arr;
    }

    private List<KPIData> flatten(Map<String, List<KPIData>> m) {
        List<KPIData> all = new ArrayList<>();
        if (m != null) {
            m.values().forEach(list -> { if (list != null) all.addAll(list); });
        }
        return all;
    }

    // ============================================================
    // PROCESSA PROJETO
    // ============================================================

    public KPIEngineResult calculateForProject(
        String project,
        JSONArray plans,
        JSONArray runs,
        JSONArray existingHistory
    ) {

        if (plans == null) plans = new JSONArray();
        if (runs == null) runs = new JSONArray();
        if (existingHistory == null) existingHistory = new JSONArray();

        Map<String, List<JSONObject>> planGroups =
            matcher.groupPlansByRelease(plans, project);

        Map<String, List<JSONObject>> runGroups =
            matcher.groupRunsByRelease(runs, project);

        Map<String, List<KPIData>> kpisByRelease = new LinkedHashMap<>();
        Map<String, ReleaseSummaryRow> summaries = new LinkedHashMap<>();

        JSONArray hist = existingHistory;

        for (String rel : planGroups.keySet()) {

            List<JSONObject> planList = planGroups.get(rel);
            if (planList == null || planList.isEmpty()) continue;

            List<JSONObject> runList =
                runGroups.getOrDefault(rel, Collections.emptyList());

            ReleaseContext ctx = matcher.matchPlan(planList.get(0), project);
            if (ctx == null) continue;

            RunStats stats = aggregate(runList);

            // =============================================
            // 1) Calcular KPIs independentes
            // =============================================
            List<KPIData> kpis = new ArrayList<>();

            for (ReleaseKPIService svc : services) {
                try {
                    List<KPIData> result =
                        svc.calculateForRelease(project, ctx, planList, runList, stats);

                    if (result != null && !result.isEmpty()) {
                        kpis.addAll(result);
                    }

                } catch (Exception e) {
                    LoggerUtils.error("Erro KPI " + svc.getKpiKey() +
                        " release=" + rel + " projeto=" + project, e);
                }
            }

            kpisByRelease.put(rel, kpis);

            // =============================================
            // 2) Criar ReleaseSummaryRow (consolidado por release)
            // =============================================
            ReleaseSummaryRow summary =
                summaryAggregator.aggregate(project, ctx, planList, stats);

            summaries.put(rel, summary);

            // =============================================
            // 3) Salvar KPIs no histórico
            // =============================================
            for (KPIData k : kpis) {
                JSONObject snap = history.createSnapshot(ctx, k);
                hist = history.upsertSnapshot(hist, snap);
            }
        }

        return new KPIEngineResult(kpisByRelease, summaries, hist);
    }

    // ============================================================
    // AGREGA RUNSTATS
    // ============================================================

    private RunStats aggregate(List<JSONObject> runs) {

        if (runs == null || runs.isEmpty()) {
            return new RunStats.Builder().build();
        }

        int total = 0, untested = 0;
        int passed = 0, failed = 0, skipped = 0, blocked = 0,
            retest = 0, invalid = 0, inProgress = 0;

        for (JSONObject r : runs) {

            RunStats s = aggregator.aggregateFromRun(r);
            if (s == null) continue;

            total += s.getTotalCases();
            untested += s.getUntestedCases();
            passed += s.getPassed();
            failed += s.getFailed();
            skipped += s.getSkipped();
            blocked += s.getBlocked();
            retest += s.getRetest();
            invalid += s.getInvalid();
            inProgress += s.getInProgress();
        }

        int executed = Math.max(0, total - untested);

        return new RunStats.Builder()
            .withTotalCases(total)
            .withUntestedCases(untested)
            .withExecutedCases(executed)
            .withPassed(passed)
            .withFailed(failed)
            .withSkipped(skipped)
            .withBlocked(blocked)
            .withRetest(retest)
            .withInvalid(invalid)
            .withInProgress(inProgress)
            .build();
    }
}
