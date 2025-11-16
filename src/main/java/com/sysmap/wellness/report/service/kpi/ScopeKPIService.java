package com.sysmap.wellness.report.service.kpi;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.ReleaseUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Serviço responsável pelo cálculo do KPI de Escopo (quantidade total de Test Cases
 * planejados para uma determinada release de um projeto).
 *
 * Agora utiliza ReleaseUtils para normalização e comparação.
 */
public class ScopeKPIService {

    public KPIData calculate(JSONObject consolidated, String project, String releaseId) {

        LoggerUtils.step("📌 Calculando KPI de Escopo — Projeto: " + project);

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            LoggerUtils.warn("⚠️ Nenhum Test Plan encontrado para " + project);
            return KPIData.of(
                "plannedScope",
                "Escopo planejado",
                0,
                project,
                releaseId
            );
        }

        int totalCases = 0;

        for (int i = 0; i < plans.length(); i++) {

            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            String title = plan.optString("title", "");

            // 💡 Agora usando o utilitário centralizado
            if (!ReleaseUtils.isPlanFromRelease(title, releaseId)) {
                LoggerUtils.info("❌ Ignorando Test Plan (não pertence à release): " + title);
                continue;
            }

            int cases = plan.optInt("cases_count", 0);

            LoggerUtils.info("🧩 Test Plan reconhecido para a release [" +
                releaseId + "] → " + cases + " cases (" + title + ")");

            totalCases += cases;
        }

        LoggerUtils.success("📌 Total de cases planejados: " + totalCases);

        return KPIData.of(
            "plannedScope",
            "Escopo planejado",
            totalCases,
            project,
            releaseId
        );
    }
}
