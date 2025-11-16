package com.sysmap.wellness.report.service.kpi;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * KPI 2 — Cobertura da Release (releaseCoverage)
 * ---------------------------------------------------------------
 * Mede o percentual de Test Cases planejados que foram executados
 * ao menos uma vez na release considerada.
 *
 * Fórmula:
 *   cobertura = (executados / planejados) * 100
 *
 * Características:
 * - Arredondamento sem casas decimais
 * - formattedValue exibe "NN%"
 * - key = "releaseCoverage"
 * - O valor representa SE PELO MENOS UMA execução ocorreu
 *
 * Este KPI aparece:
 *   - no Resumo Executivo da release atual
 *   - no Painel Consolidado (quando configurado)
 *
 * Compatível com multi-release (KPIEngine atribui o grupo/release).
 */
public class KPIReleaseCoverageService {

    /**
     * Calcula o KPI de Cobertura da Release.
     *
     * @param consolidated JSON consolidado contendo plans e runs.
     * @param project      Nome do projeto.
     * @param releaseId    Identificador da release.
     * @return KPIData com chave oficial "releaseCoverage".
     */
    public KPIData calculate(JSONObject consolidated, String project, String releaseId) {

        LoggerUtils.step("📊 Calculando KPI: Cobertura da Release — " + project);

        int planned = countPlannedCases(consolidated);
        int executed = countExecutedCases(consolidated);

        if (planned == 0) {
            LoggerUtils.warn("⚠ Nenhum caso planejado encontrado. Cobertura = 0%");
            return new KPIData(
                "releaseCoverage",               // ✓ chave oficial
                "Cobertura da Release",
                0,
                "0%",
                "→",
                "Percentual de casos executados ao menos uma vez",
                true,
                project,
                releaseId
            );
        }

        double percent = (executed * 100.0) / planned;
        long rounded = Math.round(percent);

        LoggerUtils.success("📌 Cobertura calculada: " + rounded + "% (" + executed + "/" + planned + ")");

        return new KPIData(
            "releaseCoverage",                   // ✓ chave oficial
            "Cobertura da Release",
            rounded,
            rounded + "%",
            "→",
            "Percentual de casos executados ao menos uma vez",
            true,
            project,
            releaseId
        );
    }

    /**
     * Conta quantos Test Cases foram planejados na release.
     *
     * Cada Test Plan possui um campo "cases_count".
     *
     * @param consolidated JSON original completo do projeto.
     * @return total de test cases planejados.
     */
    private int countPlannedCases(JSONObject consolidated) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null) return 0;

        int total = 0;
        for (int i = 0; i < plans.length(); i++) {

            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            total += plan.optInt("cases_count", 0);
        }

        return total;
    }

    /**
     * Conta quantos test cases foram executados pelo menos uma vez.
     *
     * A contagem é SET-BASED, garantindo que um case executado 20 vezes
     * é contado apenas uma vez.
     *
     * status executado = passed | failed | blocked | skipped
     *
     * @param consolidated JSON com runs e results.
     * @return quantidade de cases com ao menos uma execução.
     */
    private int countExecutedCases(JSONObject consolidated) {

        JSONArray runs = consolidated.optJSONArray("run");
        if (runs == null) return 0;

        Set<Integer> executed = new HashSet<>();

        for (int i = 0; i < runs.length(); i++) {

            JSONObject run = runs.optJSONObject(i);
            if (run == null) continue;

            JSONArray results = run.optJSONArray("results");
            if (results == null) continue;

            for (int j = 0; j < results.length(); j++) {

                JSONObject r = results.optJSONObject(j);
                if (r == null) continue;

                String status = r.optString("status", "").toLowerCase().trim();
                int caseId = r.optInt("case_id", -1);

                if (caseId > 0 && isExecutedStatus(status)) {
                    executed.add(caseId);
                }
            }
        }

        return executed.size();
    }

    /**
     * Determina se um status representa execução válida para este KPI.
     *
     * Observação:
     *   skipped = conta como executado (por decisão do cliente)
     *
     * @param status status textual padronizado.
     * @return true se status for executado.
     */
    private boolean isExecutedStatus(String status) {
        return status.equals("passed")
            || status.equals("failed")
            || status.equals("blocked")
            || status.equals("skipped");
    }
}
