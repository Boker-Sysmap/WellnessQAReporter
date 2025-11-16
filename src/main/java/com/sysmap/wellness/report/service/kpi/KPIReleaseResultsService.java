package com.sysmap.wellness.report.service.kpi;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * KPI: Resultados da Release (Passed, Failed, Blocked, Retest)
 *
 * Gera 4 KPIs individuais:
 *   releaseResults.passedPct
 *   releaseResults.failedPct
 *   releaseResults.blockedPct      (inclui skipped)
 *   releaseResults.retestPct
 *
 * Cada KPI é salvo separadamente no histórico e exibido no Painel Consolidado.
 */
public class KPIReleaseResultsService {

    public static final String KPI_PREFIX = "releaseResults";

    /**
     * Calcula os 4 KPIs do grupo releaseResults somando TODOS os runs da release.
     *
     * @param runs      array de runs filtrados pela release (consolidated["run"])
     * @param project   código do projeto
     * @param releaseId identificador da release
     */
    public List<KPIData> calculate(JSONArray runs, String project, String releaseId) {

        if (runs == null || runs.isEmpty()) {
            LoggerUtils.warn("KPIReleaseResultsService: nenhum run encontrado para a release " + releaseId);
            return buildNA(project, releaseId);
        }

        int passed  = 0;
        int failed  = 0;
        int blocked = 0;
        int retest  = 0;

        // 🔢 Somar resultados de TODOS os runs da release
        for (int i = 0; i < runs.length(); i++) {

            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONObject stats = run.optJSONObject("stats");

            if (stats != null) {
                passed  += stats.optInt("passed", 0);
                failed  += stats.optInt("failed", 0);

                // ⭐ NOVA REGRA: skipped conta como blocked
                int blockedCount = stats.optInt("blocked", 0);
                int skippedCount = stats.optInt("skipped", 0);

                blocked += blockedCount + skippedCount;

                retest  += stats.optInt("retest", 0);
                continue;
            }

            // Fallback formato antigo (campos no próprio run)
            passed  += run.optInt("passed", 0);
            failed  += run.optInt("failed", 0);

            // ⭐ NOVA REGRA NO FORMATO ANTIGO TAMBÉM
            blocked += run.optInt("blocked", 0) + run.optInt("skipped", 0);

            retest  += run.optInt("retest", 0);
        }

        long base = (long) passed + failed + blocked + retest;

        if (base <= 0L) {
            // Há runs, mas nenhum caso com status considerado (base = 0)
            return List.of(
                create("passedPct",  0.0, project, releaseId),
                create("failedPct",  0.0, project, releaseId),
                create("blockedPct", 0.0, project, releaseId),
                create("retestPct",  0.0, project, releaseId)
            );
        }

        return List.of(
            create("passedPct",  round(passed  * 100.0 / base), project, releaseId),
            create("failedPct",  round(failed  * 100.0 / base), project, releaseId),
            create("blockedPct", round(blocked * 100.0 / base), project, releaseId),
            create("retestPct",  round(retest  * 100.0 / base), project, releaseId)
        );
    }

    /**
     * Constrói os 4 KPIs com valor N/A (null), exibidos como "N/A" no painel.
     */
    private List<KPIData> buildNA(String project, String releaseId) {
        List<KPIData> list = new ArrayList<>();
        list.add(create("passedPct",  null, project, releaseId));
        list.add(create("failedPct",  null, project, releaseId));
        list.add(create("blockedPct", null, project, releaseId));
        list.add(create("retestPct",  null, project, releaseId));
        return list;
    }

    /**
     * Cria um KPIData usando o padrão oficial: KPIData.of(...)
     */
    private KPIData create(String suffix, Double value, String project, String releaseId) {

        String key = KPI_PREFIX + "." + suffix;
        String label =
            "passedPct".equals(suffix)  ? "Passed (%)"  :
                "failedPct".equals(suffix)  ? "Failed (%)"  :
                    "blockedPct".equals(suffix) ? "Blocked (%)" :
                        "Retest (%)";

        return KPIData.of(
            key,
            label,
            value,
            project,
            releaseId
        );
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
