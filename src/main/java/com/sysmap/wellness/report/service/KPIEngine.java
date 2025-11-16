package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KPIEngine {

    private final KPIHistoryService historyService = new KPIHistoryService();
    private final ScopeKPIService scopeService = new ScopeKPIService();

    // Regex: PROJ-AAAA-MM-RNN + qualquer coisa depois
    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("^([A-Z0-9_]+)-(\\d{4})-(\\d{2})-(R\\d{2}).*");

    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidatedData,
        String fallbackReleaseId
    ) {
        Map<String, List<KPIData>> result = new LinkedHashMap<>();

        LoggerUtils.section("📊 KPIEngine: calculando KPIs para todos os projetos");

        for (String project : consolidatedData.keySet()) {

            LoggerUtils.step("📊 KPIEngine: calculando KPIs para o projeto " + project);

            JSONObject data = consolidatedData.get(project);

            String releaseId = detectReleaseId(data, project, fallbackReleaseId);

            LoggerUtils.info("🔎 Release selecionada automaticamente: " + releaseId);

            List<KPIData> kpis = calculateKPIsForProject(data, project, releaseId);

            result.put(project, kpis);

            // Gravar histórico da release correspondente
            historyService.saveAll(project, releaseId, kpis);
        }

        return result;
    }

    private List<KPIData> calculateKPIsForProject(JSONObject consolidated,
                                                  String project,
                                                  String releaseId) {

        List<KPIData> kpis = new ArrayList<>();

        // Aqui adicionamos os KPIs que vão entrando
        kpis.add(scopeService.calculate(consolidated, project, releaseId));

        return kpis;
    }

    /**
     * Detecta a release mais recente com base nos Test Plans.
     * Se não encontrar nada válido, usa o fallback.
     */
    private String detectReleaseId(JSONObject consolidated,
                                   String project,
                                   String fallback) {

        JSONArray arr = consolidated.optJSONArray("plan");
        if (arr == null || arr.isEmpty()) return fallback;

        List<String> releases = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject plan = arr.optJSONObject(i);
            if (plan == null) continue;

            String title = plan.optString("title", null);
            String rid = extractReleaseId(title);
            if (rid != null) {
                releases.add(rid);
            }
        }

        if (releases.isEmpty()) {
            LoggerUtils.warn("⚠ Nenhuma release válida encontrada em 'plan' para " + project);
            return fallback;
        }

        // Mais recente primeiro (lexicográfico funciona pelo padrão AAAA-MM-RNN)
        releases.sort(Comparator.reverseOrder());

        LoggerUtils.info("🔎 Releases detectadas para " + project + ": " + releases);

        return releases.get(0);
    }

    /**
     * Extrai releaseId a partir do título do Test Plan.
     * Remove espaços, normaliza hífen e ignora qualquer sufixo.
     */
    private String extractReleaseId(String title) {
        if (title == null) return null;

        String clean = title
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();

        Matcher m = RELEASE_PATTERN.matcher(clean);
        if (!m.matches()) {
            return null;
        }

        String proj = m.group(1);
        String ano  = m.group(2);
        String mes  = m.group(3);
        String rnn  = m.group(4);

        return proj + "-" + ano + "-" + mes + "-" + rnn;
    }
}
