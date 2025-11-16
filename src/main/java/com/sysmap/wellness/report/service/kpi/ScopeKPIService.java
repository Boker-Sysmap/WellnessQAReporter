package com.sysmap.wellness.report.service.kpi;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Serviço responsável pelo cálculo do KPI de Escopo (quantidade total de Test Cases
 * planejados para uma determinada release de um projeto). Este KPI reflete o volume
 * total de cobertura planejada no ciclo de testes, de acordo com os Test Plans do Qase.
 *
 * <p>O cálculo é baseado na soma do campo {@code cases_count} de todos os Test Plans
 * associados à release analisada. A associação entre Test Plan e release é feita por
 * uma normalização do título do plano, permitindo identificar Test Plans cujo título
 * inicia com o identificador normalizado da release.</p>
 *
 * <p>Este serviço é utilizado internamente pelo {@code KPIService} e pelo
 * {@code KPIEngine} como parte do pipeline de cálculo de KPIs multi-release.</p>
 */
public class ScopeKPIService {

    /**
     * Calcula o KPI de Escopo (total de test cases planejados) para a release
     * informada. O processo consiste em:
     *
     * <ol>
     *   <li>Recuperar todos os Test Plans do consolidated;</li>
     *   <li>Normalizar o identificador da release e os títulos dos Test Plans;</li>
     *   <li>Selecionar apenas os planos cujo título comece com o releaseId normalizado;</li>
     *   <li>Somar o campo {@code cases_count} de cada Test Plan relacionado;</li>
     *   <li>Retornar um {@link KPIData} representando o total acumulado.</li>
     * </ol>
     *
     * <p>Se nenhum Test Plan for encontrado, ou se não houver planos compatíveis com a
     * release informada, o KPI retornará valor zero.</p>
     *
     * @param consolidated Consolidated.json referente ao projeto.
     * @param project      Nome do projeto analisado.
     * @param releaseId    Identificador da release alvo.
     * @return KPIData contendo o total de test cases planejados para a release.
     */
    public KPIData calculate(JSONObject consolidated, String project, String releaseId) {

        LoggerUtils.step("📌 Calculando KPI de Escopo — Projeto: " + project);

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            LoggerUtils.warn("⚠️ Nenhum Test Plan encontrado para " + project);
            return KPIData.simple("Escopo planejado", 0, project, releaseId);
        }

        int totalCases = 0;
        String releaseIdNorm = normalizeRelease(releaseId);

        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            String title = plan.optString("title", "");
            String titleNorm = normalizeTitle(title);

            if (!titleNorm.startsWith(releaseIdNorm)) {
                continue;
            }

            int cases = plan.optInt("cases_count", 0);
            LoggerUtils.info("🧩 Test Plan encontrado para " + releaseId + " → " + cases + " cases");
            totalCases += cases;
        }

        LoggerUtils.success("📌 Total de cases planejados: " + totalCases);

        return KPIData.simple("Escopo planejado", totalCases, project, releaseId);
    }

    /**
     * Normaliza o título de um Test Plan para permitir comparação com o releaseId.
     * O processo inclui:
     * <ul>
     *   <li>Conversão para maiúsculas;</li>
     *   <li>Remoção de espaços;</li>
     *   <li>Troca de traços do tipo unicode (–) por traços simples (-);</li>
     *   <li>Remoção de caracteres de formatação;</li>
     *   <li>Trim final.</li>
     * </ul>
     *
     * @param title Título original do Test Plan.
     * @return Título normalizado para comparação.
     */
    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();
    }

    /**
     * Normaliza o identificador da release para permitir comparação com os títulos
     * dos Test Plans. A normalização é idêntica à realizada para títulos.
     *
     * @param releaseId Identificador original da release.
     * @return Release normalizada (uppercase, sem espaços e com hífens padronizados).
     */
    private String normalizeRelease(String releaseId) {
        if (releaseId == null) return "";
        return releaseId
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();
    }
}
