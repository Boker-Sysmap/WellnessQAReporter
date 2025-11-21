package com.sysmap.wellness.report.service;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.util.*;

public class DefectAnalyticalService {

    /**
     * Consolida e normaliza defeitos de todos os projetos.
     */
    public Map<String, JSONArray> prepareData(Map<String, JSONObject> consolidatedData) {

        Map<String, JSONArray> projectDefects = new LinkedHashMap<>();
        LoggerUtils.step("🔎 Iniciando consolidação de defeitos...");

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {

            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            // --- Arrays seguros (agora consistentes com o novo consolidado)
            JSONArray defectsArray = safeArray(projectData, "defects", "defect");
            JSONArray usersArray   = safeArray(projectData, "users",   "user");
            JSONArray resultsArray = safeArray(projectData, "results", "result"); // CORRETO
            JSONArray casesArray   = safeArray(projectData, "cases", "case");
            JSONArray suitesArray  = safeArray(projectData, "suites", "suite");

            LoggerUtils.step("📦 Projeto " + projectKey +
                " | defects=" + defectsArray.length() +
                " | results=" + resultsArray.length() +
                " | cases=" + casesArray.length() +
                " | suites=" + suitesArray.length());

            // --- Mapas de acesso rápido
            Map<String, JSONObject> resultsByHash = buildResultsByHash(resultsArray);
            Map<Integer, JSONObject> casesById    = buildCasesById(casesArray);
            Map<Integer, JSONObject> suitesById   = buildSuitesById(suitesArray);
            Map<Integer, JSONObject> usersById    = buildUsersById(usersArray);

            JSONArray normalizedDefects = new JSONArray();

            // =====================================================
            //   ENRIQUECIMENTO DE CADA DEFEITO
            // =====================================================
            for (int i = 0; i < defectsArray.length(); i++) {

                JSONObject defect = defectsArray.getJSONObject(i);
                JSONObject enriched = new JSONObject();

                enriched.put("orig_id", defect.opt("id"));
                enriched.put("title", defect.optString("title", ""));
                enriched.put("status", defect.optString("status", ""));
                enriched.put("severity", defect.optString("severity", ""));
                enriched.put("priority", defect.optString("priority", ""));
                enriched.put("created_at", defect.optString("created_at", ""));
                enriched.put("updated_at", defect.optString("updated_at", ""));
                enriched.put("resolved_at", defect.optString("resolved_at", ""));

                // --- Custom field "ticket" (id 4)
                String ticket = extractCustomField(defect, 4);
                enriched.put("ticket", ticket == null ? "N/A" : ticket);

                // --- Usuário responsável
                enriched.put("reported_by", resolveReportedBy(defect, usersById));

                // --- Funcionalidade (suite)
                String suiteTitle = resolveSuiteTitleForDefect(
                    defect,
                    resultsByHash,
                    casesById,
                    suitesById,
                    resultsArray
                );
                enriched.put("suite", suiteTitle == null ? "Não identificada" : suiteTitle);

                // --- Data ISO final
                String reportDateIso = computeReportDateIso(
                    defect,
                    enriched.optString("severity", ""),
                    enriched.optString("created_at", "")
                );
                enriched.put("report_date_iso", reportDateIso == null ? "" : reportDateIso);

                // Original (auditoria)
                enriched.put("source", defect);

                normalizedDefects.put(enriched);
            }

            projectDefects.put(projectKey, normalizedDefects);
            LoggerUtils.success("✅ " + normalizedDefects.length() + " defeitos preparados para " + projectKey);
        }

        return projectDefects;
    }

    // =============================================================
    // MAP BUILDERS
    // =============================================================

    private Map<String, JSONObject> buildResultsByHash(JSONArray results) {
        Map<String, JSONObject> map = new HashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String hash = r.optString("hash", null);
            if (hash != null && !hash.isBlank()) {
                map.put(hash, r);
            }
        }
        return map;
    }

    private Map<Integer, JSONObject> buildCasesById(JSONArray arr) {
        Map<Integer, JSONObject> m = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.optInt("id", -1);
            if (id >= 0) m.put(id, o);
        }
        return m;
    }

    private Map<Integer, JSONObject> buildSuitesById(JSONArray arr) {
        Map<Integer, JSONObject> m = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.optInt("id", -1);
            if (id >= 0) m.put(id, o);
        }
        return m;
    }

    private Map<Integer, JSONObject> buildUsersById(JSONArray arr) {
        Map<Integer, JSONObject> m = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.optInt("id", -1);
            if (id >= 0) m.put(id, o);
        }
        return m;
    }

    // =============================================================
    // CORRELAÇÃO DEFECT → RESULT → CASE → SUITE
    // =============================================================

    private String resolveSuiteTitleForDefect(
        JSONObject defect,
        Map<String, JSONObject> resultsByHash,
        Map<Integer, JSONObject> casesById,
        Map<Integer, JSONObject> suitesById,
        JSONArray allResults
    ) {

        // SUPORTE: defect.results (A: ["hash"], B: [{"hash": "..."}])
        if (defect.has("results")) {

            Object rObj = defect.get("results");

            if (rObj instanceof JSONArray) {
                JSONArray ra = (JSONArray) rObj;

                for (int i = 0; i < ra.length(); i++) {

                    String hash = null;

                    Object ref = ra.get(i);
                    if (ref instanceof String) hash = (String) ref;
                    if (ref instanceof JSONObject) hash = ((JSONObject) ref).optString("hash");

                    if (hash != null && resultsByHash.containsKey(hash)) {

                        JSONObject result = resultsByHash.get(hash);
                        int caseId = result.optInt("case_id", -1);

                        if (caseId > 0) {
                            String suite = suiteTitleFromCaseId(caseId, casesById, suitesById);
                            if (suite != null) return suite;
                        }
                    }
                }
            }
        }

        // TENTATIVA 2 — procurar por defect_id em results
        String defectId = String.valueOf(defect.opt("id"));

        for (int i = 0; i < allResults.length(); i++) {
            JSONObject r = allResults.optJSONObject(i);
            if (r == null) continue;

            // ✔ CORREÇÃO AQUI
            if (r.optString("defect_id", "").equals(defectId)) {
                int caseId = r.optInt("case_id", -1);
                if (caseId > 0) {
                    String suite = suiteTitleFromCaseId(caseId, casesById, suitesById);
                    if (suite != null) return suite;
                }
            }
        }

        // TENTATIVA 3 — fallback: defect.case_id
        int caseId = defect.optInt("case_id", -1);
        if (caseId > 0) {
            return suiteTitleFromCaseId(caseId, casesById, suitesById);
        }

        return null;
    }

    private String suiteTitleFromCaseId(
        int caseId,
        Map<Integer, JSONObject> casesById,
        Map<Integer, JSONObject> suitesById
    ) {

        JSONObject c = casesById.get(caseId);
        if (c == null) return null;

        int suiteId = c.optInt("suite_id", -1);
        if (suiteId < 0) return null;

        JSONObject s = suitesById.get(suiteId);
        if (s == null) return null;

        return s.optString("title", "Sem Nome");
    }

    // =============================================================
    // CUSTOM FIELDS
    // =============================================================
    private String extractCustomField(JSONObject defect, int customId) {
        if (!defect.has("custom_fields")) return null;

        try {
            Object cf = defect.get("custom_fields");

            if (cf instanceof JSONArray) {
                JSONArray arr = (JSONArray) cf;

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);

                    if (o.optInt("id", -1) == customId) {
                        Object v = o.opt("value");
                        return v == null ? null : String.valueOf(v);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // =============================================================
    // DATAS
    // =============================================================

    private String computeReportDateIso(JSONObject defect, String severity, String createdAtIso) {

        OffsetDateTime created = parseOffset(createdAtIso);
        String reportField = extractCustomField(defect, 17);

        if (reportField == null || reportField.isBlank()) {
            return created != null ? created.toString() : "";
        }

        OffsetDateTime reportDT = parseOffset(reportField);
        LocalDate reportDate;

        if (reportDT != null) {
            reportDate = reportDT.toLocalDate();
        } else {
            try {
                reportDate = LocalDate.parse(reportField);
            } catch (Exception e) {
                return created != null ? created.toString() : "";
            }
        }

        LocalTime timeBase =
            severity.equalsIgnoreCase("critical") ||
                severity.equalsIgnoreCase("blocker") ||
                severity.equalsIgnoreCase("major")
                ? (created != null ? created.toLocalTime() : LocalTime.NOON)
                : LocalTime.of(11, 0);

        ZoneOffset offset = created != null
            ? created.getOffset()
            : OffsetDateTime.now().getOffset();

        return OffsetDateTime.of(reportDate, timeBase, offset).toString();
    }

    private OffsetDateTime parseOffset(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso).atOffset(OffsetDateTime.now().getOffset());
            } catch (Exception ignored) {}
        }
        return null;
    }

    // =============================================================
    // SAFE ARRAY
    // =============================================================

    private JSONArray safeArray(JSONObject source, String... keys) {
        for (String key : keys) {
            if (source.has(key)) {
                Object val = source.get(key);
                if (val instanceof JSONArray) return (JSONArray) val;
                if (val instanceof JSONObject) return new JSONArray().put(val);
            }
        }
        return new JSONArray();
    }

    // =============================================================
    // REPORTE POR USER
    // =============================================================

    private String resolveReportedBy(JSONObject defect, Map<Integer, JSONObject> usersById) {

        if (!defect.has("member_id")) return "Desconhecido";

        int id = defect.optInt("member_id", -1);
        JSONObject user = usersById.get(id);
        if (user == null) return "Desconhecido";

        return user.optString("name",
            user.optString("title",
                user.optString("full_name", "Desconhecido")));
    }
}
