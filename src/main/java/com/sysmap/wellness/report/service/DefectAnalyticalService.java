package com.sysmap.wellness.report.service;

import com.sysmap.wellness.util.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Serviço responsável por consolidar e normalizar os dados
 * do endpoint 'defect' (ou 'defects') para o relatório
 * "Gestão de Defeitos - Analítico".
 *
 * <p>
 * Funcionalidades principais:
 * <ul>
 *   <li>Enriquecer cada defect com: suite (funcionalidade), ticket (custom_field id=4),</li>
 *   <li>reportadoBy (user.name via member_id)</li>
 *   <li>reportDate (data base calculada usando custom_field id=17 + regras de severidade)</li>
 * </ul>
 * </p>
 *
 * <p>Regra de relacionamento para funcionalidade (suite.title):
 * <pre>
 * defect.results -> result.hash -> result.case_id -> case.suite_id -> suite.title
 * </pre>
 * </p>
 *
 * <p>Se campos não puderem ser encontrados, são aplicados valores default
 * (por exemplo: "Não identificada", "Desconhecido", etc.).</p>
 */
public class DefectAnalyticalService {

    /**
     * Extrai e prepara os dados de defeitos de cada projeto.
     *
     * @param consolidatedData Mapa de projetos → JSONObject contendo endpoints (case, suite, result, defect, user etc)
     * @return Mapa de projetos → JSONArray com defeitos normalizados/prontos para relatório
     */
    public Map<String, JSONArray> prepareData(Map<String, JSONObject> consolidatedData) {
        Map<String, JSONArray> projectDefects = new LinkedHashMap<>();

        LoggerUtils.step("🔎 Iniciando consolidação de defeitos...");

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {
            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            JSONArray defectsArray = safeArray(projectData, "defects", "defect");
            JSONArray usersArray = safeArray(projectData, "users", "user");
            JSONArray resultsArray = safeArray(projectData, "results", "result");
            JSONArray casesArray = safeArray(projectData, "cases", "case");
            JSONArray suitesArray = safeArray(projectData, "suites", "suite");

            LoggerUtils.step("📦 Projeto " + projectKey +
                    ": defects=" + defectsArray.length() +
                    ", users=" + usersArray.length() +
                    ", results=" + resultsArray.length() +
                    ", cases=" + casesArray.length() +
                    ", suites=" + suitesArray.length());

            // Prepara mapas auxiliares para busca rápida
            Map<String, JSONObject> resultsByHash = buildResultsByHash(resultsArray);
            Map<Integer, JSONObject> casesById = buildCasesById(casesArray);
            Map<Integer, JSONObject> suitesById = buildSuitesById(suitesArray);
            Map<Integer, JSONObject> usersById = buildUsersById(usersArray);

            JSONArray normalizedDefects = new JSONArray();

            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                JSONObject enriched = new JSONObject();

                // Campos básicos preservados
                enriched.put("orig_id", defect.opt("id"));
                enriched.put("title", defect.optString("title", ""));
                enriched.put("status", defect.optString("status", ""));
                enriched.put("severity", defect.optString("severity", ""));
                enriched.put("priority", defect.optString("priority", ""));
                enriched.put("created_at", defect.optString("created_at", ""));
                enriched.put("updated_at", defect.optString("updated_at", ""));
                enriched.put("resolved_at", defect.optString("resolved_at", ""));

                // === Ticket: custom_field id == 4 ===
                String ticket = extractCustomField(defect, 4);
                enriched.put("ticket", ticket == null ? "N/A" : ticket);

                // === Reportado por: member_id -> user.name ===
                String reportedBy = "Desconhecido";
                if (defect.has("member_id")) {
                    int memberId = defect.optInt("member_id", -1);
                    JSONObject user = usersById.get(memberId);
                    if (user != null) {
                        reportedBy = user.optString("name", user.optString("title", user.optString("full_name", "Desconhecido")));
                    }
                }
                enriched.put("reported_by", reportedBy);

                // === Determina funcionalidade (suite.title) via relacionamento com results -> case -> suite ===
                String suiteTitle = resolveSuiteTitleForDefect(defect, resultsByHash, casesById, suitesById, resultsArray);
                enriched.put("suite", suiteTitle == null ? "Não identificada" : suiteTitle);

                // === ReportDate (base para 'Reportado em') ===
                // Regras:
                //  - custom_fields.id==17 é "Report Date" (data base)
                //  - If severity in [Critical, Blocker, Major] => use date from Report Date + time from created_at
                //  - Else => date from Report Date + 11:00:00
                //  - If Report Date not present => fallback to created_at
                String reportDateIso = computeReportDateIso(defect, enriched.optString("severity", ""), enriched.optString("created_at", ""));
                enriched.put("report_date_iso", reportDateIso == null ? "" : reportDateIso);

                // inclui objeto original caso precise
                enriched.put("source", defect);

                normalizedDefects.put(enriched);
            }

            projectDefects.put(projectKey, normalizedDefects);
            LoggerUtils.success("✅ " + normalizedDefects.length() + " defeitos preparados para " + projectKey);
        }

        return projectDefects;
    }

    // ================= Helper builders =================

    private Map<String, JSONObject> buildResultsByHash(JSONArray results) {
        Map<String, JSONObject> map = new HashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String hash = r.optString("hash", null);
            if (hash != null && !hash.isBlank()) map.put(hash, r);
        }
        return map;
    }

    private Map<Integer, JSONObject> buildCasesById(JSONArray cases) {
        Map<Integer, JSONObject> map = new HashMap<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            int id = c.optInt("id", -1);
            if (id >= 0) map.put(id, c);
        }
        return map;
    }

    private Map<Integer, JSONObject> buildSuitesById(JSONArray suites) {
        Map<Integer, JSONObject> map = new HashMap<>();
        for (int i = 0; i < suites.length(); i++) {
            JSONObject s = suites.getJSONObject(i);
            int id = s.optInt("id", -1);
            if (id >= 0) map.put(id, s);
        }
        return map;
    }

    private Map<Integer, JSONObject> buildUsersById(JSONArray users) {
        Map<Integer, JSONObject> map = new HashMap<>();
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.getJSONObject(i);
            int id = u.optInt("id", -1);
            if (id >= 0) map.put(id, u);
        }
        return map;
    }

    // ================= Extraction helpers =================

    /**
     * Extrai o valor de um custom field com id específico no defect.
     * Assumimos que defect pode conter "custom_fields" como JSONArray de objetos { "id": X, "value": ... }
     *
     * @param defect objeto defect
     * @param customId id do campo personalizado
     * @return valor do campo (string) ou null se não existir
     */
    private String extractCustomField(JSONObject defect, int customId) {
        if (!defect.has("custom_fields")) return null;
        try {
            Object cf = defect.get("custom_fields");
            if (cf instanceof JSONArray) {
                JSONArray arr = (JSONArray) cf;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    if (o.optInt("id", -1) == customId) {
                        Object val = o.opt("value");
                        return val == null ? null : String.valueOf(val);
                    }
                }
            } else if (cf instanceof JSONObject) {
                JSONObject o = (JSONObject) cf;
                if (o.optInt("id", -1) == customId) return String.valueOf(o.opt("value"));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Resolve a suite.title para um defect aplicando as várias estratégias:
     * 1) Se defect tiver "results" array com entries contendo "hash", consulta resultsByHash para achar result -> case_id
     * 2) Se não, verifica em resultsArray algum result que referência esse defect (campo defect_id)
     * 3) A partir do case_id obtém case -> suite_id -> suite.title
     *
     * @return título da suite ou null
     */
    private String resolveSuiteTitleForDefect(JSONObject defect,
                                              Map<String, JSONObject> resultsByHash,
                                              Map<Integer, JSONObject> casesById,
                                              Map<Integer, JSONObject> suitesById,
                                              JSONArray allResults) {
        // 1) results referenciados diretamente no defect
        if (defect.has("results")) {
            try {
                Object rObj = defect.get("results");
                if (rObj instanceof JSONArray) {
                    JSONArray ra = (JSONArray) rObj;
                    for (int i = 0; i < ra.length(); i++) {
                        JSONObject ref = ra.getJSONObject(i);
                        String hash = ref.optString("hash", null);
                        if (hash != null && resultsByHash.containsKey(hash)) {
                            JSONObject result = resultsByHash.get(hash);
                            int caseId = result.optInt("case_id", -1);
                            if (caseId > 0) {
                                String t = suiteTitleFromCaseId(caseId, casesById, suitesById);
                                if (t != null) return t;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2) Buscar em allResults por resultados que tenham defect_id = defect.id
        Object defIdObj = defect.opt("id");
        String defIdStr = defIdObj == null ? null : String.valueOf(defIdObj);
        if (allResults != null) {
            for (int i = 0; i < allResults.length(); i++) {
                JSONObject r = allResults.getJSONObject(i);
                // result might have field "defect_id" or "defects" etc.
                if (r.has("defect_id") && !r.isNull("defect_id")) {
                    if (String.valueOf(r.opt("defect_id")).equals(defIdStr)) {
                        int caseId = r.optInt("case_id", -1);
                        if (caseId > 0) {
                            String t = suiteTitleFromCaseId(caseId, casesById, suitesById);
                            if (t != null) return t;
                        }
                    }
                }
                // also check hash relation: sometimes defect may include result hash value in some field
                String hash = r.optString("hash", null);
                if (hash != null && defect.toString().contains(hash)) {
                    int caseId = r.optInt("case_id", -1);
                    if (caseId > 0) {
                        String t = suiteTitleFromCaseId(caseId, casesById, suitesById);
                        if (t != null) return t;
                    }
                }
            }
        }

        // 3) fallback: try to find case id in defect (some APIs embed case_id)
        int possibleCaseId = defect.optInt("case_id", -1);
        if (possibleCaseId > 0) {
            String t = suiteTitleFromCaseId(possibleCaseId, casesById, suitesById);
            if (t != null) return t;
        }

        return null;
    }

    private String suiteTitleFromCaseId(int caseId, Map<Integer, JSONObject> casesById, Map<Integer, JSONObject> suitesById) {
        JSONObject c = casesById.get(caseId);
        if (c != null) {
            int suiteId = c.optInt("suite_id", -1);
            if (suiteId > 0) {
                JSONObject s = suitesById.get(suiteId);
                if (s != null) return s.optString("title", "Sem Nome");
            }
        }
        return null;
    }

    /**
     * Computa a data ISO (OffsetDateTime string) que será usada como "reportado em" (report_date_iso).
     *
     * Regras:
     *  - busca custom_field id=17 (Report Date). Se existir, usa a data dele como base.
     *  - se severity ∈ {Critical, Blocker, Major} → combina data base com hora do created_at.
     *  - caso contrário → combina data base com hora fixa 11:00:00.
     *  - se não existir Report Date → usa created_at (como OffsetDateTime).
     *
     * Retorna string ISO (OffsetDateTime.toString) ou null se não for possível.
     */
    private String computeReportDateIso(JSONObject defect, String severity, String createdAtIso) {
        // tenta extrair createdAt
        OffsetDateTime createdOdt = parseOffsetDateTime(createdAtIso);

        String reportField = extractCustomField(defect, 17); // Report Date
        if (reportField == null || reportField.isBlank()) {
            // fallback para created_at
            return createdOdt == null ? null : createdOdt.toString();
        }

        // tenta interpretar reportField (pode ser data sem hora)
        OffsetDateTime reportOdt = parseOffsetDateTime(reportField);
        LocalDate reportDate = null;
        if (reportOdt != null) {
            reportDate = reportOdt.toLocalDate();
        } else {
            // tenta LocalDate parse (yyyy-MM-dd)
            try {
                reportDate = LocalDate.parse(reportField);
            } catch (DateTimeParseException ignored) {
                // fallback: try other common formats? For safety, fallback to created date
                reportDate = createdOdt == null ? null : createdOdt.toLocalDate();
            }
        }

        if (reportDate == null) return createdOdt == null ? null : createdOdt.toString();

        // define hora base
        LocalTime timeBase;
        String sev = severity == null ? "" : severity.trim().toLowerCase();
        if ("critical".equalsIgnoreCase(severity) || "blocker".equalsIgnoreCase(severity) || "major".equalsIgnoreCase(severity)) {
            // hora do created_at se possível
            timeBase = (createdOdt != null) ? createdOdt.toLocalTime() : LocalTime.NOON;
        } else {
            // hora fixa 11:00
            timeBase = LocalTime.of(11, 0);
        }

        // monta OffsetDateTime combinando com offset de createdOdt (se disponível) ou system default offset
        ZoneOffset offset = (createdOdt != null) ? createdOdt.getOffset() : OffsetDateTime.now().getOffset();
        OffsetDateTime finalOdt = OffsetDateTime.of(reportDate, timeBase, offset);
        return finalOdt.toString();
    }

    private OffsetDateTime parseOffsetDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (DateTimeParseException e) {
            // tenta LocalDateTime (sem offset)
            try {
                LocalDateTime ldt = LocalDateTime.parse(iso);
                return ldt.atOffset(OffsetDateTime.now().getOffset());
            } catch (Exception ex) {
                // ignora
            }
        }
        return null;
    }

    /** Retorna um JSONArray seguro mesmo que o campo não exista. */
    private JSONArray safeArray(JSONObject source, String... keys) {
        for (String key : keys) {
            if (source == null) continue;
            if (source.has(key)) {
                Object val = source.get(key);
                if (val instanceof JSONArray) return (JSONArray) val;
                if (val instanceof JSONObject) {
                    JSONArray single = new JSONArray();
                    single.put((JSONObject) val);
                    return single;
                }
            }
        }
        return new JSONArray();
    }
}
