package com.sysmap.wellness.core.qase.gateway;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * DataConsolidator (versão modularizada)
 *
 * Orquestra o carregamento de todos os endpoints e consolida
 * os dados brutos num único JSON por projeto.
 *
 * Após o patch:
 *  - Plans possuem "runs" associados via plan_id (feito pelo ReleaseEnricher).
 *  - JSON consolidado pode ser salvo em arquivo configurável.
 */
public class DataConsolidator {

    private static final String UNKNOWN_RELEASE = "UNKNOWN-RELEASE";

    // Serviços especializados
    private final ConsolidatorFileLoader fileLoader = new ConsolidatorFileLoader();
    private final ConsolidatorReleaseEnricher releaseEnricher = new ConsolidatorReleaseEnricher();
    private final ConsolidatorRunResultsLoader runResultsLoader = new ConsolidatorRunResultsLoader();
    private final ConsolidatorGlobalMetadata globalMetadata = new ConsolidatorGlobalMetadata();

    /**
     * Consolida todos os projetos listados no config.properties.
     *
     * @return mapa projeto → consolidated JSON.
     */
    public Map<String, JSONObject> consolidateAll() {

        LoggerUtils.divider();
        LoggerUtils.step("📦 Consolidando dados (RUN-BASED) — versão revisada");

        Map<String, JSONObject> consolidated = new LinkedHashMap<>();

        List<String> projects = ConfigManager.getProjects();
        List<String> endpoints = ConfigManager.getActiveEndpoints();

        for (String project : projects) {

            LoggerUtils.section("🔹 Projeto: " + project);

            JSONObject projectData = new JSONObject();
            Map<String, JSONObject> releaseMetaById = new LinkedHashMap<>();

            // --------------------------------------------------------
            // 1 — CARREGAR ENDPOINTS (case, suite, defect, plan, run)
            // --------------------------------------------------------
            for (String endpoint : endpoints) {

                JSONArray entities = fileLoader.loadEndpoint(project, endpoint);
                projectData.put(endpoint, entities);

                MetricsCollector.incrementBy("jsonRecordsLoaded", entities.length());

                // enrich PLAN → indexa + prepara "runs"
                // enrich RUN → associa com plan via "plan_id"
                if ("plan".equalsIgnoreCase(endpoint) || "run".equalsIgnoreCase(endpoint)) {
                    releaseEnricher.enrich(entities, releaseMetaById, endpoint);
                }
            }

            // --------------------------------------------------------
            // 2 — RELEASE GLOBAL (não mexe em plan_id)
            // --------------------------------------------------------
            globalMetadata.applyGlobal(projectData, releaseMetaById);

            // --------------------------------------------------------
            // 3 — CARREGAR RUN_RESULTS
            // --------------------------------------------------------
            Map<String, JSONArray> runResults = runResultsLoader.load(project);
            projectData.put("run_results", new JSONObject(runResults));

            // --------------------------------------------------------
            // 4 — SALVAR CONSOLIDADO EM ARQUIVO
            // --------------------------------------------------------
            saveConsolidatedToFile(project, projectData);

            consolidated.put(project, projectData);

            LoggerUtils.success("✔ Consolidação do projeto " + project + " concluída.");
        }

        LoggerUtils.success("🏁 Consolidação finalizada.");
        return consolidated;
    }

    /**
     * Salva o JSON consolidado num arquivo apropriado,
     * usando o diretório configurado no config.properties.
     */
    private void saveConsolidatedToFile(String project, JSONObject data) {
        try {
            String baseDir = ConfigManager.get("consolidated.output.dir");
            if (baseDir == null || baseDir.isBlank()) {
                baseDir = "output/consolidated";
            }

            Path outDir = Path.of(baseDir);
            Files.createDirectories(outDir);

            Path outFile = outDir.resolve(project + "_consolidated.json");

            Files.writeString(outFile, data.toString(2), StandardCharsets.UTF_8);

            LoggerUtils.info("💾 Consolidado salvo em: " + outFile.toAbsolutePath());

        } catch (Exception e) {
            LoggerUtils.error("❌ Falha ao salvar consolidado para " + project, e);
        }
    }
}
