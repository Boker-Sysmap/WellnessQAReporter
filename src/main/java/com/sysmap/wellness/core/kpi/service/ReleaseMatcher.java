package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.model.ReleaseContext;
import com.sysmap.wellness.utils.IdentifierParser;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Responsável por:
 * - Interpretar títulos de Test Plan / Test Run via IdentifierParser;
 * - Construir ReleaseContext com base nos mnemônicos;
 * - Agrupar planos e execuções por officialId (version_environment);
 *
 * NÃO usa plan_id em lugar nenhum.
 */
public class ReleaseMatcher {

    public interface ReleaseNameParser {
        IdentifierParser.ParsedIdentifier parse(String raw);
    }

    private final ReleaseNameParser parser;

    public ReleaseMatcher(ReleaseNameParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser não pode ser nulo");
    }

    /**
     * Constrói o ReleaseContext com todos os metadados extraídos e,
     * AGORA COM CORREÇÃO: inclui projectKey.
     */
    private ReleaseContext toContext(IdentifierParser.ParsedIdentifier parsed, String projectKey) {
        if (parsed == null) return null;

        Map<String, Object> v = parsed.getValues();

        return new ReleaseContext.Builder()
            .withVersion((String) v.get("version"))
            .withEnvironment((String) v.get("environment"))
            .withSprint((String) v.get("sprint"))
            .withPlatform((String) v.get("platform"))
            .withLanguage((String) v.get("language"))
            .withTestType((String) v.get("testType"))
            .withReleaseName(parsed.getRawIdentifier())
            .withProjectKey(projectKey) // 🔥 CORREÇÃO ESSENCIAL
            .build();
    }

    private ReleaseContext matchInternal(JSONObject obj, String projectKey) {
        if (obj == null) return null;

        String title = obj.optString("title", null);
        if (title == null || title.isBlank()) return null;

        IdentifierParser.ParsedIdentifier parsed = parser.parse(title);
        if (parsed == null) {
            return null; // título fora do padrão → ignorar (conforme regra do usuário)
        }

        return toContext(parsed, projectKey);
    }

    // =====================================================================
    // API para Plans
    // =====================================================================

    public ReleaseContext matchPlan(JSONObject plan, String projectKey) {
        return matchInternal(plan, projectKey);
    }

    public Map<String, List<JSONObject>> groupPlansByRelease(JSONArray plans, String projectKey) {

        Map<String, List<JSONObject>> grouped = new LinkedHashMap<>();

        if (plans == null) return grouped;

        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;

            ReleaseContext ctx = matchPlan(p, projectKey);
            if (ctx == null) continue;

            String rel = ctx.getOfficialId();
            grouped.computeIfAbsent(rel, x -> new ArrayList<>()).add(p);
        }

        return grouped;
    }

    // =====================================================================
    // API para Runs
    // =====================================================================

    public ReleaseContext matchRun(JSONObject run, String projectKey) {
        return matchInternal(run, projectKey);
    }

    public Map<String, List<JSONObject>> groupRunsByRelease(JSONArray runs, String projectKey) {

        Map<String, List<JSONObject>> grouped = new LinkedHashMap<>();

        if (runs == null) return grouped;

        for (int i = 0; i < runs.length(); i++) {
            JSONObject r = runs.optJSONObject(i);
            if (r == null) continue;

            ReleaseContext ctx = matchRun(r, projectKey);
            if (ctx == null) continue;

            String rel = ctx.getOfficialId();
            grouped.computeIfAbsent(rel, x -> new ArrayList<>()).add(r);
        }

        return grouped;
    }
}
