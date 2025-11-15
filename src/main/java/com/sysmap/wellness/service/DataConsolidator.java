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
 * DataConsolidator RUN-BASED
 *
 * Consolida todo o conteúdo JSON baixado pelo QaseClient, incluindo:
 *  - cases
 *  - suites
 *  - defects
 *  - runs
 *  - run_results (crucial para descobrir funcionalidade)
 *
 * A estrutura final para cada projeto fica assim:
 *
 * {
 *     "case": [...],
 *     "suite": [...],
 *     "defect": [...],
 *     "run": [...],
 *
 *     "run_results": {
 *         "6": [...],
 *         "16": [...],
 *         ...
 *     }
 * }
 *
 * Isso permite ao DefectAnalyticalService localizar a funcionalidade
 * via:
 *
 *  defect.runs[0] → run_results[runId] → result.case_id → case.suite_id → suite.title
 */
public class DataConsolidator {

    private static final Path JSON_DIR = Path.of("output", "json");

    public Map<String, JSONObject> consolidateAll() {

        LoggerUtils.divider();
        LoggerUtils.step("📦 Consolidando dados a partir dos arquivos JSON locais (modo RUN-BASED)");

        Map<String, JSONObject> consolidated = new LinkedHashMap<>();

        List<String> projects = ConfigManager.getProjects();
        List<String> activeEndpoints = ConfigManager.getActiveEndpoints();

        for (String project : projects) {

            LoggerUtils.section("🔹 Projeto: " + project);
            JSONObject projectData = new JSONObject();

            // -----------------------------
            // 1. Carregar ENDPOINTS NORMAIS
            // -----------------------------
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

                    LoggerUtils.step(String.format("📄 %s_%s.json → %d registros",
                            project, endpoint, entities.length()));

                    projectData.put(endpoint, entities);

                    MetricsCollector.incrementBy("jsonRecordsLoaded", entities.length());

                } catch (Exception e) {
                    LoggerUtils.error("Erro ao processar endpoint " + endpoint + "@" + project, e);
                }
            }

            // -----------------------------------------------
            // 2. CARREGAR TODOS OS RUN_RESULTS (super importante)
            // -----------------------------------------------

            Map<String, JSONArray> runResultsMap = new LinkedHashMap<>();

            try {
                // Identificar TODOS os arquivos run_<id>_results.json
                DirectoryStream<Path> stream = Files.newDirectoryStream(JSON_DIR,
                        project + "_run_*_results.json");

                for (Path runFile : stream) {

                    String fileName = runFile.getFileName().toString();
                    // nome esperado: PROJECT_run_<id>_results.json
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

            // Anexa ao consolidated
            projectData.put("run_results", new JSONObject(runResultsMap));

            // -----------------------------------
            // 3. Registro final do projeto
            // -----------------------------------
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

    // ==========================================================
    // Helpers
    // ==========================================================

    /** Extrai runId do nome do arquivo PROJECT_run_<id>_results.json */
    private String extractRunId(String filename) {

        // FULLY_run_16_results.json
        try {
            String[] parts = filename.split("_");

            // parts = [0]=FULLY , [1]=run , [2]=16 , [3]=results.json
            if (parts.length < 4) return null;

            String candidate = parts[2];
            // tirar ".json" se vier
            if (candidate.contains(".")) {
                candidate = candidate.substring(0, candidate.indexOf('.'));
            }

            return candidate;

        } catch (Exception e) {
            return null;
        }
    }

    /** JSON tolerant parser */
    private JSONArray parseJsonEntities(String raw) {
        try {
            raw = raw.trim();

            // Caso seja array puro
            if (raw.startsWith("[")) {
                return new JSONArray(raw);
            }

            JSONObject parsed = new JSONObject(raw);

            // Caso mais comum: result.entities
            if (parsed.has("result")) {
                Object r = parsed.get("result");

                if (r instanceof JSONObject) {
                    JSONObject ro = (JSONObject) r;

                    if (ro.has("entities") && ro.get("entities") instanceof JSONArray)
                        return ro.getJSONArray("entities");

                    // fallback: qualquer array dentro de result
                    for (String key : ro.keySet()) {
                        if (ro.get(key) instanceof JSONArray)
                            return ro.getJSONArray(key);
                    }
                }

                if (r instanceof JSONArray) {
                    return (JSONArray) r;
                }
            }

            // fallback generalizado
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
