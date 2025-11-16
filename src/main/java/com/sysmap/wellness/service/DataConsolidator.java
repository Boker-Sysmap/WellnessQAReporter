package com.sysmap.wellness.service;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * <h1>DataConsolidator – Mecanismo de Consolidação RUN-BASED</h1>
 *
 * <p>
 * Este serviço consolida todos os artefatos JSON previamente baixados do Qase
 * (por meio do {@code QaseClient}) em uma estrutura única por projeto.
 * </p>
 *
 * <p>
 * O método {@link #consolidateAll()} lê os arquivos presentes em
 * <code>output/json/</code> e reconstrói uma visão agregada contendo:
 * </p>
 *
 * <ul>
 *     <li><b>cases</b> – Lista de casos de teste do projeto;</li>
 *     <li><b>suites</b> – Hierarquia funcional (funcionalidades);</li>
 *     <li><b>defects</b> – Defeitos vinculados ao projeto;</li>
 *     <li><b>runs</b> – Execuções realizadas;</li>
 *     <li><b>run_results</b> – Resultados por runId (com case_id → suite_id);</li>
 * </ul>
 *
 * <p>
 * Esta abordagem permite resolução precisa da funcionalidade (suite) responsável por um defeito,
 * utilizando a cadeia:
 * </p>
 *
 * <pre>
 * defect.runs[*]
 *     → run_results[runId]
 *     → result.case_id
 *     → case.suite_id
 *     → suite.title
 * </pre>
 *
 * <h2>Formato final consolidado por projeto:</h2>
 *
 * <pre>
 * {
 *   "case": [...],
 *   "suite": [...],
 *   "defect": [...],
 *   "run": [...],
 *   "run_results": {
 *       "6":   [...],
 *       "16":  [...],
 *       ...
 *   }
 * }
 * </pre>
 *
 * <p>
 * Toda a infraestrutura é <b>RUN-BASED</b>, garantindo que cada defeito seja relacionado ao contexto
 * exato da execução onde ocorreu.
 * </p>
 */
public class DataConsolidator {

    /** Diretório onde estão armazenados os arquivos JSON exportados do QaseClient. */
    private static final Path JSON_DIR = Path.of("output", "json");

    /**
     * Realiza a consolidação completa de todos os projetos definidos em
     * {@link ConfigManager#getProjects()}.
     *
     * <p>
     * Para cada projeto:
     * </p>
     *
     * <ol>
     *   <li>Carrega JSONs básicos (cases, suites, defects, runs, etc.);</li>
     *   <li>Reconstrói <b>run_results</b> multiplicando por runId;</li>
     *   <li>Gera um objeto JSON completo contendo toda a estrutura unificada;</li>
     * </ol>
     *
     * <p>
     * Isto garante que os serviços analíticos possam correlacionar corretamente:
     * caso, execução, defeito, severidade e tempo.
     * </p>
     *
     * @return mapa com chave = código do projeto, valor = JSON consolidado
     */
    public Map<String, JSONObject> consolidateAll() {

        LoggerUtils.divider();
        LoggerUtils.step("📦 Consolidando dados a partir dos arquivos JSON locais (modo RUN-BASED)");

        Map<String, JSONObject> consolidated = new LinkedHashMap<>();

        List<String> projects = ConfigManager.getProjects();
        List<String> activeEndpoints = ConfigManager.getActiveEndpoints();

        for (String project : projects) {

            LoggerUtils.section("🔹 Projeto: " + project);
            JSONObject projectData = new JSONObject();

            // ------------------------------------------------
            // 1) Carregamento dos Endpoints Principais (cases, suites, defects, runs, etc.)
            // ------------------------------------------------
            for (String endpoint : activeEndpoints) {
                try {
                    Path file = JSON_DIR.resolve(project + "_" + endpoint + ".json");

                    if (!Files.exists(file)) {
                        LoggerUtils.warn("⚠️ Arquivo não encontrado: " + file);
                        continue;
                    }

                    String raw = Files.readString(file).trim();
                    if (raw.isBlank()) continue;

                    JSONArray entities = parseJsonEntities(raw);

                    LoggerUtils.step(String.format(
                        "📄 %s_%s.json → %d registros",
                        project, endpoint, entities.length()
                    ));

                    projectData.put(endpoint, entities);
                    MetricsCollector.incrementBy("jsonRecordsLoaded", entities.length());

                } catch (Exception e) {
                    LoggerUtils.error("Erro ao processar endpoint " + endpoint + "@" + project, e);
                }
            }

            // ------------------------------------------------
            // 2) Carregamento dos RUN_RESULTS (essencial)
            // ------------------------------------------------
            Map<String, JSONArray> runResultsMap = new LinkedHashMap<>();

            try {
                DirectoryStream<Path> stream = Files.newDirectoryStream(
                    JSON_DIR,
                    project + "_run_*_results.json"
                );

                for (Path runFile : stream) {

                    String fileName = runFile.getFileName().toString();
                    String runId = extractRunId(fileName);

                    if (runId == null) {
                        LoggerUtils.warn("⚠️ Nome inválido (não extraí runId): " + fileName);
                        continue;
                    }

                    String raw = Files.readString(runFile).trim();
                    if (raw.isBlank()) continue;

                    JSONArray runResults = parseJsonEntities(raw);

                    LoggerUtils.step(String.format(
                        "📘 %s → runId=%s → %d results",
                        fileName, runId, runResults.length()
                    ));

                    runResultsMap.put(runId, runResults);
                }

            } catch (IOException e) {
                LoggerUtils.error("Erro ao listar arquivos run_results", e);
            }

            projectData.put("run_results", new JSONObject(runResultsMap));

            // ------------------------------------------------
            // 3) Registro final do projeto
            // ------------------------------------------------
            consolidated.put(project, projectData);

            LoggerUtils.success(String.format(
                "📦 Projeto %s consolidado com %d endpoints + %d run_results",
                project,
                projectData.length(),
                runResultsMap.size()
            ));
        }

        LoggerUtils.success("🏁 Consolidação (RUN-BASED) concluída.");
        return consolidated;
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /**
     * Extrai o {@code runId} de um arquivo nomeado no padrão:
     *
     * <pre>
     * PROJECT_run_16_results.json
     * </pre>
     *
     * @param filename nome do arquivo
     * @return runId extraído (ex: "16"), ou null se inválido
     */
    private String extractRunId(String filename) {
        try {
            String[] parts = filename.split("_");

            // parts expected:
            // [0]=PROJECT, [1]=run, [2]=<id>, [3]=results.json
            if (parts.length < 4) return null;

            String candidate = parts[2];

            if (candidate.contains(".")) {
                candidate = candidate.substring(0, candidate.indexOf('.'));
            }

            return candidate;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parser tolerante utilizado para interpretar os arquivos JSON vindos do Qase.
     *
     * <p>
     * O método tenta automaticamente os seguintes formatos:
     * </p>
     *
     * <ul>
     *   <li>Array JSON puro: <code>[...]</code></li>
     *   <li><code>{"result":{"entities":[...]}}</code></li>
     *   <li><code>{"result":[...]}</code></li>
     *   <li>Qualquer chave que contenha um JSONArray;</li>
     * </ul>
     *
     * <p>
     * Caso o conteúdo seja inválido ou inesperado, retorna-se um array vazio,
     * garantindo robustez do pipeline de consolidação.
     * </p>
     *
     * @param raw conteúdo JSON original lido do arquivo
     * @return lista de entidades extraídas
     */
    private JSONArray parseJsonEntities(String raw) {
        try {
            raw = raw.trim();

            if (raw.startsWith("[")) {
                return new JSONArray(raw);
            }

            JSONObject parsed = new JSONObject(raw);

            if (parsed.has("result")) {
                Object r = parsed.get("result");

                if (r instanceof JSONObject) {
                    JSONObject ro = (JSONObject) r;

                    if (ro.has("entities") && ro.get("entities") instanceof JSONArray)
                        return ro.getJSONArray("entities");

                    for (String key : ro.keySet()) {
                        if (ro.get(key) instanceof JSONArray)
                            return ro.getJSONArray(key);
                    }
                }

                if (r instanceof JSONArray) {
                    return (JSONArray) r;
                }
            }

            for (String key : parsed.keySet()) {
                if (parsed.get(key) instanceof JSONArray)
                    return parsed.getJSONArray(key);
            }

        } catch (Exception e) {
            LoggerUtils.warn("⚠️ JSON inválido → retornando array vazio");
        }

        return new JSONArray();
    }
}
