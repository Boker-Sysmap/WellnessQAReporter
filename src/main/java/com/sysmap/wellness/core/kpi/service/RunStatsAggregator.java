package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.model.RunStats;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

/**
 * Responsável por interpretar o bloco {@code stats} de um run.json do Qase
 * e convertê-lo em uma estrutura normalizada {@link RunStats}.
 *
 * <p>Regras principais:</p>
 * <ul>
 *     <li>Utiliza os campos numéricos retornados pelo Qase sempre que presentes.</li>
 *     <li>Considera como executados: passed, failed, blocked, skipped, retest.</li>
 *     <li>Se necessário, recalcula executados com base em {@code total - untested}.</li>
 *     <li>Garante que nenhum valor negativo seja propagado.</li>
 * </ul>
 */
public class RunStatsAggregator {

    /**
     * Converte o objeto JSON de uma run do Qase em {@link RunStats}.
     *
     * @param runJson JSONObject da run inteira (contendo o campo {@code stats}).
     * @return instância normalizada de {@link RunStats}.
     */
    public RunStats aggregateFromRun(JSONObject runJson) {
        if (runJson == null) {
            LoggerUtils.warn("RunStatsAggregator.aggregateFromRun - runJson nulo, retornando estatísticas vazias.");
            return new RunStats.Builder().build();
        }

        JSONObject stats = runJson.optJSONObject("stats");
        if (stats == null) {
            LoggerUtils.warn("RunStatsAggregator.aggregateFromRun - runJson sem campo 'stats', retornando estatísticas vazias.");
            return new RunStats.Builder().build();
        }

        int total = safeInt(stats, "total");
        int untested = safeInt(stats, "untested");
        int passed = safeInt(stats, "passed");
        int failed = safeInt(stats, "failed");
        int blocked = safeInt(stats, "blocked");
        int skipped = safeInt(stats, "skipped");
        int retest = safeInt(stats, "retest");
        int invalid = safeInt(stats, "invalid");
        int inProgress = safeInt(stats, "in_progress");

        // executados = passed + failed + blocked + skipped + retest
        int executed = Math.max(0, passed + failed + blocked + skipped + retest);

        // Caso o Qase retorne algo inconsistente, usamos como fallback total - untested
        if (total > 0) {
            int executedByDiff = Math.max(0, total - Math.max(0, untested));
            if (executed == 0 && executedByDiff > 0) {
                executed = executedByDiff;
            } else if (executed > executedByDiff && executedByDiff > 0) {
                // Se o diff total-untested indicar um valor menor, assumimos o menor
                executed = executedByDiff;
            }
        }

        return new RunStats.Builder()
            .withTotalCases(total)
            .withUntestedCases(untested)
            .withExecutedCases(executed)
            .withPassed(passed)
            .withFailed(failed)
            .withBlocked(blocked)
            .withSkipped(skipped)
            .withRetest(retest)
            .withInvalid(invalid)
            .withInProgress(inProgress)
            .build();
    }

    /**
     * Recupera um inteiro de forma tolerante, evitando exceções de tipo.
     *
     * @param json JSONObject de origem.
     * @param field nome do campo.
     * @return valor inteiro não negativo.
     */
    protected int safeInt(JSONObject json, String field) {
        if (json == null || field == null) {
            return 0;
        }
        try {
            int value = json.optInt(field, 0);
            return Math.max(0, value);
        } catch (Exception e) {
            LoggerUtils.warn("RunStatsAggregator.safeInt - erro ao ler campo '" + field + "': " + e.getMessage());
            return 0;
        }
    }
}
