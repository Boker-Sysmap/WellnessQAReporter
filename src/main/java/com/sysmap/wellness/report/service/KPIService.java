package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service legado para cálculo de KPIs de um único projeto.
 * Mantido por compatibilidade, mas alinhado com o KPIEngine.
 */
public class KPIService {

    private final ScopeKPIService scopeKPI = new ScopeKPIService();

    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("^([A-Z0-9_]+)-(\\d{4})-(\\d{2})-(R\\d{2}).*");

    public List<KPIData> calculateKPIs(JSONObject consolidated, String project) {

        LoggerUtils.section("📊 Calculando KPIs — Projeto: " + project);

        List<KPIData> result = new ArrayList<>();

        String releaseId = detectReleaseId(consolidated, project);

        if (releaseId == null) {
            LoggerUtils.warn("⚠ Nenhuma release válida encontrada. KPIs não podem ser calculados.");
            return result;
        }

        LoggerUtils.success("🏷 Release ativa: " + releaseId);

        result.add(scopeKPI.calculate(consolidated, project, releaseId));

        LoggerUtils.success("📦 KPIs calculados: " + result.size());
        return result;
    }

    private String detectReleaseId(JSONObject consolidated, String project) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null) return null;

        List<String> releases = new ArrayList<>();

        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;

            String title = p.optString("title", "").trim();
            if (title.isEmpty()) continue;

            String releaseId = extractReleaseId(title);
            if (releaseId != null) releases.add(releaseId);
        }

        if (releases.isEmpty()) {
            LoggerUtils.warn("⚠ Nenhum Test Plan corresponde ao formato de release para " + project);
            return null;
        }

        releases.sort(Comparator.reverseOrder());

        String latest = releases.get(0);
        LoggerUtils.info("🔎 Releases detectadas: " + releases);
        LoggerUtils.success("➡ Release mais recente selecionada: " + latest);

        return latest;
    }

    private String extractReleaseId(String title) {
        if (title == null) return null;

        String clean = title
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();

        Matcher m = RELEASE_PATTERN.matcher(clean);
        if (!m.matches()) return null;

        String proj = m.group(1);
        String ano  = m.group(2);
        String mes  = m.group(3);
        String rnn  = m.group(4);

        return proj + "-" + ano + "-" + mes + "-" + rnn;
    }
}
