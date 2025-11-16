package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
import com.sysmap.wellness.report.service.kpi.KPIReleaseCoverageService;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço legado responsável pelo cálculo de KPIs em contexto de um único projeto.
 *
 * (documentação original preservada)
 */
public class KPIService {

    /** Serviço especializado para cálculo do KPI de Escopo (plannedScope). */
    private final ScopeKPIService scopeKPI = new ScopeKPIService();

    /** Serviço especializado para cálculo do KPI de Cobertura da Release (releaseCoverage). */
    private final KPIReleaseCoverageService coverageKPI = new KPIReleaseCoverageService();

    /** Regex de identificação de ReleaseId em Test Plans. */
    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("^([A-Z0-9_]+)-(\\d{4})-(\\d{2})-(R\\d{2}).*");

    /**
     * Executa o pipeline de cálculo de KPIs para um único projeto.
     */
    public List<KPIData> calculateKPIs(JSONObject consolidated, String project) {

        LoggerUtils.section("📊 Calculando KPIs — Projeto: " + project);

        List<KPIData> result = new ArrayList<>();

        // Detecta release principal
        String releaseId = detectReleaseId(consolidated, project);

        if (releaseId == null) {
            LoggerUtils.warn("⚠ Nenhuma release válida encontrada. KPIs não podem ser calculados.");
            return result;
        }

        LoggerUtils.success("🏷 Release ativa: " + releaseId);

        // KPI 1 — Escopo Planejado
        KPIData plannedScope = scopeKPI.calculate(consolidated, project, releaseId);
        result.add(plannedScope);

        // KPI 2 — Cobertura da Release
        KPIData releaseCoverage = coverageKPI.calculate(consolidated, project, releaseId);
        result.add(releaseCoverage);

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
