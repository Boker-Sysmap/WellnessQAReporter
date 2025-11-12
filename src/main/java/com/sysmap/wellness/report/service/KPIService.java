package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula os principais indicadores executivos (KPIs) de cada projeto.
 * <p>
 * Retorna KPIs já classificados como percentuais ou absolutos.
 * </p>
 */
public class KPIService {

    /**
     * Calcula os KPIs para o projeto fornecido.
     *
     * @param projectData Dados consolidados do projeto.
     * @param projectName Nome opcional do projeto (apenas para log).
     * @return Lista de KPIs calculados.
     */
    public List<KPIData> calculateKPIs(JSONObject projectData, String projectName) {
        List<KPIData> kpis = new ArrayList<>();

        if (projectData == null) return kpis;

        JSONArray cases = projectData.optJSONArray("case");
        JSONArray results = projectData.optJSONArray("result");
        JSONArray defects = projectData.optJSONArray("defect");

        int totalCases = cases != null ? cases.length() : 0;
        int totalExecuted = results != null ? results.length() : 0;
        int totalPassed = 0;
        int totalFailed = 0;
        int totalBlocked = 0;
        int totalDefects = defects != null ? defects.length() : 0;

        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String status = r.optString("status", "").toLowerCase();
                switch (status) {
                    case "passed":
                        totalPassed++;
                        break;
                    case "failed":
                        totalFailed++;
                        break;
                    case "blocked":
                        totalBlocked++;
                        break;
                }
            }
        }

        double approvalRate = totalExecuted > 0 ? (double) totalPassed / totalExecuted * 100.0 : 0;
        double defectRate = totalExecuted > 0 ? (double) totalDefects / totalExecuted * 100.0 : 0;
        double blockedRate = totalCases > 0 ? (double) totalBlocked / totalCases * 100.0 : 0;

        // === KPIs percentuais ===
        kpis.add(new KPIData("Taxa de Aprovação", approvalRate, "↑", "Percentual de testes aprovados", true));
        kpis.add(new KPIData("Taxa de Defeitos", defectRate, "↓", "Defeitos abertos por execução", true));
        kpis.add(new KPIData("Casos Bloqueados", blockedRate, "→", "Proporção de casos bloqueados", true));

        // === KPI absoluto ===
        kpis.add(new KPIData("Total Executado", totalExecuted, "→", "Quantidade total de casos executados", false));

        return kpis;
    }
}
