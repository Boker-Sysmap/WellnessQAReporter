package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.ReleaseUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Serviço responsável pelo cálculo do KPI de Escopo (quantidade total de Test Cases
 * planejados para uma determinada release de um projeto).
 *
 * Regra de negócio CRÍTICA:
 * ------------------------------------------------------------------
 * - O Qase não permite Test Plan sem Test Case.
 * - Portanto, em condições normais, NUNCA deveria existir plannedScope = 0
 *   para uma release válida.
 *
 * Implicações:
 * ------------------------------------------------------------------
 * - Se plannedScope = 0 ocorrer, isso é tratado como ERRO DE DADOS
 *   (problema de identificação de release, título mal formatado, etc.).
 * - Ainda assim, o KPI é retornado com valor 0 (para não quebrar o fluxo),
 *   mas um LOG DE ERRO é emitido para investigação.
 */
public class ScopeKPIService {

    public KPIData calculate(JSONObject consolidated, String project, String releaseId) {

        LoggerUtils.step("📌 Calculando KPI de Escopo — Projeto: " + project +
            " | Release: " + releaseId);

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            // Em teoria, se há release válida, SEMPRE deveria haver pelo menos 1 plan.
            LoggerUtils.error(
                "❌ plannedScope=0 para " + project + " / " + releaseId +
                    " — nenhum Test Plan encontrado no consolidated. " +
                    "Isso indica problema de dados ou de identificação da release."
            );
            return KPIData.of(
                "plannedScope",
                "Escopo planejado",
                0,
                project,
                releaseId
            );
        }

        int totalCases = 0;
        int totalPlans = 0;
        int matchedPlans = 0;

        for (int i = 0; i < plans.length(); i++) {

            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            totalPlans++;

            String title = plan.optString("title", "");

            // Filtra apenas os planos realmente pertencentes à release
            if (!ReleaseUtils.isPlanFromRelease(title, releaseId)) {
                LoggerUtils.info("❌ Ignorando Test Plan (não pertence à release " +
                    releaseId + "): " + title);
                continue;
            }

            matchedPlans++;

            int cases = plan.optInt("cases_count", 0);

            LoggerUtils.info("🧩 Test Plan reconhecido para a release [" +
                releaseId + "] → " + cases + " cases (" + title + ")");

            totalCases += cases;
        }

        // Regra forte: plannedScope NUNCA deveria ser 0 em condições normais.
        if (totalCases == 0) {
            LoggerUtils.error(
                "❌ plannedScope=0 para " + project + " / " + releaseId +
                    " — totalCases=0, totalPlans=" + totalPlans +
                    ", matchedPlans=" + matchedPlans + ". " +
                    "Isso NÃO deveria acontecer: verifique titles e release.identifier.format."
            );
        } else {
            LoggerUtils.success("📌 Total de cases planejados para " + project +
                " / " + releaseId + ": " + totalCases +
                " (plans=" + matchedPlans + "/" + totalPlans + ")");
        }

        return KPIData.of(
            "plannedScope",
            "Escopo planejado",
            totalCases,
            project,
            releaseId
        );
    }
}
