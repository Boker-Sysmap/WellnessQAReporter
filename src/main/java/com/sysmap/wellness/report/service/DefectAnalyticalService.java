package com.sysmap.wellness.report.service;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Serviço responsável por consolidar, normalizar e enriquecer os dados de defeitos
 * provenientes do Qase, produzindo uma estrutura unificada e padronizada para o
 * relatório “Gestão de Defeitos – Analítico”.
 *
 * <p>Este serviço integra diferentes fontes de informação dentro do consolidated.json,
 * correlacionando:</p>
 *
 * <ul>
 *   <li><b>defects</b> → informações principais;</li>
 *   <li><b>results</b> → ligações entre defeitos e execuções;</li>
 *   <li><b>cases</b> → identificação da funcionalidade;</li>
 *   <li><b>suites</b> → título da funcionalidade associada;</li>
 *   <li><b>users</b> → identificação do responsável;</li>
 * </ul>
 *
 * <p>Também implementa suporte total aos dois formatos oficiais do campo {@code results}
 * utilizados pelo Qase:</p>
 *
 * <ol>
 *   <li>Array simples de hashes: <code>["hash1", "hash2"]</code></li>
 *   <li>Array de objetos: <code>[{ "hash": "..." }]</code></li>
 * </ol>
 *
 * <p>O resultado final é uma estrutura enriquecida que facilita a geração de relatórios
 * executivos e analíticos com informações completas sobre cada defeito.</p>
 */
public class DefectAnalyticalService {

    /**
     * Consolida e normaliza defeitos de todos os projetos.
     *
     * <p>Para cada projeto, o método:</p>
     *
     * <ul>
     *   <li>Extrai arrays seguros de defects, results, users, suites e cases;</li>
     *   <li>Constrói mapas de lookup para acesso rápido por id/hash;</li>
     *   <li>Enriquece cada defeito com:
     *       <ul>
     *         <li>ID original;</li>
     *         <li>nome da suite (funcionalidade);</li>
     *         <li>severity, priority e status;</li>
     *         <li>usuário que reportou;</li>
     *         <li>datas normalizadas;</li>
     *         <li>ticket relacionado (custom_field id 4);</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * @param consolidatedData Map contendo consolidated.json por projeto.
     * @return Mapa projeto → array de defeitos enriquecidos.
     */
    public Map<String, JSONArray> prepareData(Map<String, JSONObject> consolidatedData) {

        Map<String, JSONArray> projectDefects = new LinkedHashMap<>();
        LoggerUtils.step("🔎 Iniciando consolidação de defeitos...");

        for (Map.Entry<String, JSONObject> entry : consolidatedData.entrySet()) {

            String projectKey = entry.getKey();
            JSONObject projectData = entry.getValue();

            // Arrays seguros contendo eventuais chaves alternativas.
            JSONArray defectsArray = safeArray(projectData, "defects", "defect");
            JSONArray usersArray = safeArray(projectData, "users", "user");
            JSONArray resultsArray = safeArray(projectData, "results", "result");
            JSONArray casesArray = safeArray(projectData, "cases", "case");
            JSONArray suitesArray = safeArray(projectData, "suites", "suite");

            LoggerUtils.step("📦 Projeto " + projectKey +
                " | defects=" + defectsArray.length() +
                " | users=" + usersArray.length() +
                " | results=" + resultsArray.length() +
                " | cases=" + casesArray.length() +
                " | suites=" + suitesArray.length());

            // Mapas de acesso rápido
            Map<String, JSONObject> resultsByHash = buildResultsByHash(resultsArray);
            Map<Integer, JSONObject> casesById = buildCasesById(casesArray);
            Map<Integer, JSONObject> suitesById = buildSuitesById(suitesArray);
            Map<Integer, JSONObject> usersById = buildUsersById(usersArray);

            JSONArray normalizedDefects = new JSONArray();

            // =====================================================
            //  ENRIQUECIMENTO DE CADA DEFEITO
            // =====================================================
            for (int i = 0; i < defectsArray.length(); i++) {

                JSONObject defect = defectsArray.getJSONObject(i);
                JSONObject enriched = new JSONObject();

                // Campos básicos
                enriched.put("orig_id", defect.opt("id"));
                enriched.put("title", defect.optString("title", ""));
                enriched.put("status", defect.optString("status", ""));
                enriched.put("severity", defect.optString("severity", ""));
                enriched.put("priority", defect.optString("priority", ""));
                enriched.put("created_at", defect.optString("created_at", ""));
                enriched.put("updated_at", defect.optString("updated_at", ""));
                enriched.put("resolved_at", defect.optString("resolved_at", ""));

                // Campo customizado: ticket
                String ticket = extractCustomField(defect, 4);
                enriched.put("ticket", ticket == null ? "N/A" : ticket);

                // Usuário que reportou
                String reportedBy = "Desconhecido";
                if (defect.has("member_id")) {
                    int memberId = defect.optInt("member_id", -1);
                    JSONObject user = usersById.get(memberId);
                    if (user != null) {
                        reportedBy = user.optString("name",
                            user.optString("title",
                                user.optString("full_name", "Desconhecido")));
                    }
                }
                enriched.put("reported_by", reportedBy);

                // Funcionalidade (suite)
                String suiteTitle = resolveSuiteTitleForDefect(
                    defect,
                    resultsByHash,
                    casesById,
                    suitesById,
                    resultsArray
                );
                enriched.put("suite", suiteTitle == null ? "Não identificada" : suiteTitle);

                // Data ISO de reporte, com regras específicas por severity
                String reportDateIso = computeReportDateIso(
                    defect,
                    enriched.optString("severity", ""),
                    enriched.optString("created_at", "")
                );
                enriched.put("report_date_iso", reportDateIso == null ? "" : reportDateIso);

                // Registro original para auditoria
                enriched.put("source", defect);

                normalizedDefects.put(enriched);
            }

            projectDefects.put(projectKey, normalizedDefects);
            LoggerUtils.success("✅ " + normalizedDefects.length() + " defeitos preparados para " + projectKey);
        }

        return projectDefects;
    }

    // =====================================================================
    // BUILD MAPS
    // =====================================================================

    /**
     * Constrói um mapa hash → result.
     *
     * @param results Array de resultados do consolidated.
     * @return Mapa hash → JSONObject result.
     */
    private Map<String, JSONObject> buildResultsByHash(JSONArray results) {
        Map<String, JSONObject> map = new HashMap<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String hash = r.optString("hash", null);
            if (hash != null && !hash.isBlank()) map.put(hash, r);
        }
        return map;
    }

    /** Constrói mapa id → caso. */
    private Map<Integer, JSONObject> buildCasesById(JSONArray arr) {
        Map<Integer, JSONObject> m = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.optInt("id", -1);
            if (id >= 0) m.put(id, o);
        }
        return m;
    }

    /** Constrói mapa id → suite (funcionalidade). */
    private Map<Integer, JSONObject> buildSuitesById(JSONArray arr) {
        Map<Integer, JSONObject> m = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.optInt("id", -1);
            if (id >= 0) m.put(id, o);
        }
        return m;
    }

    /** Constrói mapa id → usuário. */
    private Map<Integer, JSONObject> buildUsersById(JSONArray arr) {
        Map<Integer, JSONObject> m = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.optInt("id", -1);
            if (id >= 0) m.put(id, o);
        }
        return m;
    }

    // =====================================================================
    // CORRELAÇÃO: DEFECT → RESULT → CASE → SUITE
    // =====================================================================

    /**
     * Resolve a funcionalidade (suite.title) associada ao defeito,
     * utilizando uma cadeia de correlação progressiva:
     *
     * <ol>
     *   <li>Primeiro: analisa o campo defect.results em ambos os formatos;</li>
     *   <li>Segundo: busca entries de results que tenham o defect_id correspondente;</li>
     *   <li>Terceiro: verifica se o próprio defeito possui case_id direto;</li>
     *   <li>Fallback: retorna null.</li>
     * </ol>
     *
     * @return Nome da suite ou null se não identificada.
     */
    private String resolveSuiteTitleForDefect(
        JSONObject defect,
        Map<String, JSONObject> resultsByHash,
        Map<Integer, JSONObject> casesById,
        Map<Integer, JSONObject> suitesById,
        JSONArray allResults
    ) {

        // SUPORTE AOS 2 FORMATOS DE results
        if (defect.has("results")) {

            Object rObj = defect.get("results");

            if (rObj instanceof JSONArray) {
                JSONArray ra = (JSONArray) rObj;

                for (int i = 0; i < ra.length(); i++) {

                    Object ref = ra.get(i);
                    String hash = null;

                    // Formato A: ["hash"]
                    if (ref instanceof String) {
                        hash = (String) ref;
                    }
                    // Formato B: [{"hash": "..."}]
                    else if (ref instanceof JSONObject) {
                        hash = ((JSONObject) ref).optString("hash", null);
                    }

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

        // TENTATIVA 2 — Procurar por defect_id
        String defId = String.valueOf(defect.opt("id"));

        for (int i = 0; i < allResults.length(); i++) {
            JSONObject r = allResults.getJSONObject(i);

            if (r.has("defect_id")) {
                if (String.valueOf(r.opt("defect_id")).equals(defId)) {

                    int caseId = r.optInt("case_id", -1);

                    if (caseId > 0) {
                        String suite = suiteTitleFromCaseId(caseId, casesById, suitesById);
                        if (suite != null) return suite;
                    }
                }
            }
        }

        // TENTATIVA 3 — Fallback: case_id direto no defeito
        int caseId = defect.optInt("case_id", -1);
        if (caseId > 0) {
            String suite = suiteTitleFromCaseId(caseId, casesById, suitesById);
            if (suite != null) return suite;
        }

        return null;
    }

    /**
     * Obtém o nome da suite (funcionalidade) a partir do case_id.
     *
     * @return Título da suíte ou null se não encontrada.
     */
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

    // =====================================================================
    // EXTRAÇÃO DE CUSTOM FIELDS
    // =====================================================================

    /**
     * Extrai o valor de um custom_field específico dentro do defeito,
     * conforme o {@code id} fornecido.
     *
     * @param defect   Objeto JSON do defeito.
     * @param customId ID do custom_field desejado.
     * @return Texto do valor ou null se inexistente.
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
                        Object v = o.opt("value");
                        return v == null ? null : String.valueOf(v);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // =====================================================================
    // DATAS E NORMALIZAÇÃO
    // =====================================================================

    /**
     * Calcula uma data ISO de reporte para o defeito, aplicando as seguintes regras:
     *
     * <ul>
     *   <li>Se existir custom_field de reporte (id 17):
     *       <ul>
     *         <li>Se for ISO completo → usa diretamente;</li>
     *         <li>Se for apenas data → complementa horário;</li>
     *       </ul>
     *   </li>
     *   <li>Se não existir:
     *       <ul>
     *         <li>Usa created_at como fallback;</li>
     *       </ul>
     *   </li>
     *   <li>Se severity for crítica/major/blocker:
     *       <ul>
     *         <li>Preserva horário original;</li>
     *       </ul>
     *   </li>
     *   <li>Caso contrário: define horário padrão 11:00.</li>
     * </ul>
     *
     * @param defect     Defeito original.
     * @param severity   Severidade.
     * @param createdAtIso ISO de criação.
     * @return Data ISO final calculada.
     */
    private String computeReportDateIso(JSONObject defect, String severity, String createdAtIso) {

        OffsetDateTime created = parseOffsetDateTime(createdAtIso);
        String reportField = extractCustomField(defect, 17);

        if (reportField == null || reportField.isBlank()) {
            return created != null ? created.toString() : "";
        }

        OffsetDateTime reportDateTime = parseOffsetDateTime(reportField);
        LocalDate reportDate;

        if (reportDateTime != null) {
            reportDate = reportDateTime.toLocalDate();
        } else {
            try {
                reportDate = LocalDate.parse(reportField);
            } catch (Exception e) {
                return created != null ? created.toString() : "";
            }
        }

        LocalTime timeBase;

        if ("critical".equalsIgnoreCase(severity) ||
            "blocker".equalsIgnoreCase(severity) ||
            "major".equalsIgnoreCase(severity)) {
            timeBase = created != null ? created.toLocalTime() : LocalTime.NOON;
        } else {
            timeBase = LocalTime.of(11, 0);
        }

        ZoneOffset offset = created != null ? created.getOffset() : OffsetDateTime.now().getOffset();
        return OffsetDateTime.of(reportDate, timeBase, offset).toString();
    }

    /**
     * Faz parsing de strings ISO ou LocalDateTime, com tolerância a formatos
     * incompletos. Usado para datas de criação e reporte.
     *
     * @param iso Texto ISO.
     * @return OffsetDateTime ou null se inválido.
     */
    private OffsetDateTime parseOffsetDateTime(String iso) {
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

    // =====================================================================
    // SAFE ARRAY
    // =====================================================================

    /**
     * Extrai um JSONArray de forma tolerante, aceitando arrays e objetos.
     * Se o valor for um objeto, ele é envolvido em um array com único elemento.
     * Se a chave não existir, retorna array vazio.
     *
     * @param source Objeto original.
     * @param keys   Chaves possíveis.
     * @return JSONArray seguro.
     */
    private JSONArray safeArray(JSONObject source, String... keys) {
        for (String k : keys) {
            if (source.has(k)) {
                Object val = source.get(k);
                if (val instanceof JSONArray) return (JSONArray) val;
                if (val instanceof JSONObject) return new JSONArray().put(val);
            }
        }
        return new JSONArray();
    }
}
