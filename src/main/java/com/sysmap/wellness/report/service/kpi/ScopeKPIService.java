package com.sysmap.wellness.report.service.kpi;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class ScopeKPIService {

    /**
     * Calcula o KPI de Escopo (total de test cases planejados),
     * usando diretamente o campo cases_count dos Test Plans.
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

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();
    }

    private String normalizeRelease(String releaseId) {
        if (releaseId == null) return "";
        return releaseId
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();
    }
}
