package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Serviço responsável pelo cálculo de KPIs por projeto.
 *
 * Versão PREMIUM:
 *  - Usa o novo modelo KPIData (com key, group, project, formattedValue, toJson, etc.)
 *  - Continua simples de evoluir (cada KPI em um helper separado)
 *  - Trata ausência de dados com segurança (sem NPE)
 *  - Compatível com Java 8+
 *
 * Observação:
 *  Alguns KPIs mais avançados (tempo médio de resolução, prevenção de falhas,
 *  esforço economizado, curva de evolução etc.) dependem de campos específicos
 *  de datas/tempos que podem ainda não estar presentes no JSON consolidado.
 *  Nesta versão eles são expostos como 0 com descrição explicativa, para futura
 *  evolução, sem quebrar o pipeline.
 */
public class KPIService {

    public List<KPIData> calculateKPIs(JSONObject projectData, String projectCode) {
        LoggerUtils.step("📊 Calculando KPIs para o projeto: " + projectCode);

        List<KPIData> list = new ArrayList<KPIData>();

        JSONArray cases = getArray(projectData, "case");
        JSONArray results = getArray(projectData, "result");
        JSONArray defects = getArray(projectData, "defect");

        int scopeTotal = cases.length();
        int executedDistinct = countDistinctExecutedCases(results);
        int totalExecutions = results.length();
        int failedCount = countByResultStatus(results, "failed");
        int passedCount = countByResultStatus(results, "passed");
        int blockedCount = countByResultStatus(results, "blocked");
        int retestCount = countByResultStatus(results, "retest");

        double coverage = scopeTotal > 0 ? (executedDistinct * 100.0 / scopeTotal) : 0.0;
        double failRate = totalExecutions > 0 ? (failedCount * 100.0 / totalExecutions) : 0.0;
        double passRate = totalExecutions > 0 ? (passedCount * 100.0 / totalExecutions) : 0.0;
        double blockedRate = totalExecutions > 0 ? (blockedCount * 100.0 / totalExecutions) : 0.0;
        double retestRate = totalExecutions > 0 ? (retestCount * 100.0 / totalExecutions) : 0.0;

        int totalDefects = defects.length();
        Map<String, Integer> defectsBySeverity = countDefectsByField(defects, "severity");
        Map<String, Integer> defectsByStatus = countDefectsByField(defects, "status");

        // KPI 1 – Escopo total
        list.add(buildKpi(
                "scope_total",
                "Escopo – Casos planejados",
                scopeTotal,
                false,
                "Quantidade total de casos de teste planejados para a release (cases presentes no escopo).",
                projectCode,
                "Escopo"
        ));

        // KPI 2 – Casos executados
        list.add(buildKpi(
                "scope_executed",
                "Casos executados (distintos)",
                executedDistinct,
                false,
                "Quantidade de casos que tiveram ao menos uma execução registrada na release.",
                projectCode,
                "Execução"
        ));

        // KPI 3 – Cobertura %
        list.add(buildKpi(
                "coverage_percent",
                "Cobertura de execução",
                coverage,
                true,
                "Percentual do escopo planejado que foi executado (casos com ao menos uma execução).",
                projectCode,
                "Execução"
        ));

        // KPI 4 – Taxa de falhas
        list.add(buildKpi(
                "fail_rate",
                "Taxa de falhas",
                failRate,
                true,
                "Percentual de execuções com resultado Failed na release.",
                projectCode,
                "Qualidade"
        ));

        // KPI 5 – Taxa de passagens
        list.add(buildKpi(
                "pass_rate",
                "Taxa de sucesso",
                passRate,
                true,
                "Percentual de execuções com resultado Passed na release.",
                projectCode,
                "Qualidade"
        ));

        // KPI 6 – Taxa de bloqueios
        list.add(buildKpi(
                "blocked_rate",
                "Taxa de bloqueios",
                blockedRate,
                true,
                "Percentual de execuções com resultado Blocked (geralmente por ambiente/dados).",
                projectCode,
                "Estabilidade"
        ));

        // KPI 7 – Taxa de retestes
        list.add(buildKpi(
                "retest_rate",
                "Taxa de retestes",
                retestRate,
                true,
                "Percentual de execuções marcadas como Retest, indicando retrabalho e instabilidade.",
                projectCode,
                "Estabilidade"
        ));

        // KPI 8 – Total de defeitos
        list.add(buildKpi(
                "defects_total",
                "Defeitos encontrados",
                totalDefects,
                false,
                "Número total de defeitos registrados na release.",
                projectCode,
                "Defeitos"
        ));

        // KPI 9 – Defeitos por severidade
        for (Map.Entry<String, Integer> entry : defectsBySeverity.entrySet()) {
            String sev = entry.getKey();
            int count = entry.getValue();
            String key = "defects_severity_" + normalizeKey(sev);

            list.add(buildKpi(
                    key,
                    "Defeitos – Severidade " + sev,
                    count,
                    false,
                    "Quantidade de defeitos classificados com severidade '" + sev + "'.",
                    projectCode,
                    "Defeitos"
            ));
        }

        // KPI 10 – Defeitos por status
        for (Map.Entry<String, Integer> entry : defectsByStatus.entrySet()) {
            String status = entry.getKey();
            int count = entry.getValue();
            String key = "defects_status_" + normalizeKey(status);

            list.add(buildKpi(
                    key,
                    "Defeitos – Status " + status,
                    count,
                    false,
                    "Quantidade de defeitos no status '" + status + "'.",
                    projectCode,
                    "Defeitos"
            ));
        }

        // =====================================================================================
        // KPIs avançados (placeholders para futura evolução)
        // =====================================================================================

        // Tempo médio de execução (TODO: exige campo de duração nos results)
        double avgExecTime = 0.0; // TODO: implementar com base em campo duration/time_spent
        list.add(buildKpi(
                "avg_execution_time",
                "Tempo médio de execução",
                avgExecTime,
                false,
                "Tempo médio de execução dos testes na release. (TODO: implementar cálculo usando campo de duração dos resultados).",
                projectCode,
                "Performance"
        ));

        // Produtividade – execuções por hora (TODO: exige tempo total)
        double productivity = 0.0; // TODO
        list.add(buildKpi(
                "productivity_exec_per_hour",
                "Produtividade – Execuções por hora",
                productivity,
                false,
                "Número médio de execuções por hora. (TODO: requer cálculo de tempo total efetivo de execução).",
                projectCode,
                "Produtividade"
        ));

        // Esforço economizado (TODO: exige tempo médio manual e identificação de automação)
        list.add(buildKpi(
                "saved_effort_hours",
                "Esforço economizado (horas)",
                0.0,
                false,
                "Estimativa de tempo economizado pela automação. (TODO: depende do tempo médio manual e flag de testes automatizados).",
                projectCode,
                "Produtividade"
        ));

        // Tempo médio de resolução de defeitos (TODO: exige created_at/resolved_at)
        list.add(buildKpi(
                "defects_avg_resolution_time",
                "Tempo médio de resolução de defeitos",
                0.0,
                false,
                "Tempo médio entre abertura e resolução dos defeitos. (TODO: depende de datas de criação/fechamento nos dados de defeitos).",
                projectCode,
                "Defeitos"
        ));

        // Prevenção de falhas (TODO: exige data PRD vs data criação defeito)
        list.add(buildKpi(
                "defects_prevented_before_prd",
                "Defeitos prevenidos antes de PRD",
                0.0,
                false,
                "Número de defeitos identificados antes de chegar à produção. (TODO: requer datas de implantação em PRD).",
                projectCode,
                "Qualidade"
        ));

        // Indicador de estabilidade dos testes (TODO: exige histórico/flag de flakiness)
        list.add(buildKpi(
                "tests_stability_index",
                "Indicador de estabilidade dos testes",
                0.0,
                true,
                "Percentual de testes considerados estáveis (sem flakiness). (TODO: depende de análise do histórico de execuções).",
                projectCode,
                "Estabilidade"
        ));

        return list;
    }

    // =====================================================================
    // HELPERS DE ACESSO AO JSON
    // =====================================================================
    private JSONArray getArray(JSONObject obj, String key) {
        if (obj == null) return new JSONArray();
        JSONArray arr = obj.optJSONArray(key);
        return arr != null ? arr : new JSONArray();
    }

    // Conta casos distintos com base no campo "case_id" nos results.
    // Se o campo não existir, retorna o total de resultados.
    private int countDistinctExecutedCases(JSONArray results) {
        Set<Long> ids = new HashSet<Long>();
        boolean anyId = false;

        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;

            if (r.has("case_id")) {
                long id = r.optLong("case_id", -1L);
                if (id != -1L) {
                    ids.add(id);
                    anyId = true;
                }
            }
        }

        if (!anyId) {
            // fallback – sem case_id, considera cada resultado como um case
            return results.length();
        }

        return ids.size();
    }

    private int countByResultStatus(JSONArray results, String desiredStatus) {
        int count = 0;
        String target = desiredStatus.toLowerCase();

        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;

            String status = r.optString("status", "").toLowerCase();
            if (status.equals(target)) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Integer> countDefectsByField(JSONArray defects, String field) {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();

        for (int i = 0; i < defects.length(); i++) {
            JSONObject d = defects.optJSONObject(i);
            if (d == null) continue;

            String value = d.optString(field, "Não informado");
            if (value == null || value.trim().isEmpty()) {
                value = "Não informado";
            }

            Integer current = map.get(value);
            map.put(value, current == null ? 1 : current + 1);
        }

        return map;
    }

    private KPIData buildKpi(String key,
                             String name,
                             double value,
                             boolean percent,
                             String description,
                             String project,
                             String group) {

        String formatted;
        if (percent) {
            formatted = String.format(Locale.US, "%.2f%%", value);
        } else {
            formatted = String.format(Locale.US, "%.2f", value);
        }

        // Por enquanto, tendência neutra; você pode evoluir isso depois.
        String trendSymbol = "→";

        return new KPIData(
                key,
                name,
                value,
                formatted,
                trendSymbol,
                description,
                percent,
                project,
                group
        );
    }

    private String normalizeKey(String raw) {
        if (raw == null) return "unknown";
        return raw.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
}
