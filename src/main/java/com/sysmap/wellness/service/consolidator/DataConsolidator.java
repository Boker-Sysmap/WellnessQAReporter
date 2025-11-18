package com.sysmap.wellness.service.consolidator;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.service.consolidator.*;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.*;

/**
 * DataConsolidator (versão modularizada)
 *
 * Agora funciona como um ORQUESTRADOR, delegando todas as partes
 * para classes menores e testáveis.
 *
 * Mantém compatibilidade total com a versão anterior.
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
     * @return mapa de PROJECT → consolidated JSON
     */
    public Map<String, JSONObject> consolidateAll() {

        LoggerUtils.divider();
        LoggerUtils.step("📦 Consolidando dados (RUN-BASED) — versão modularizada");

        Map<String, JSONObject> consolidated = new LinkedHashMap<>();

        List<String> projects = ConfigManager.getProjects();
        List<String> endpoints = ConfigManager.getActiveEndpoints();

        for (String project : projects) {

            LoggerUtils.section("🔹 Projeto: " + project);

            JSONObject projectData = new JSONObject();
            Map<String, JSONObject> releaseMetaById = new LinkedHashMap<>();

            // -----------------------------------------------
            // 1 — CARREGAR ENDPOINTS (case, suite, defect, plan, run)
            // -----------------------------------------------
            for (String endpoint : endpoints) {

                JSONArray entities = fileLoader.loadEndpoint(project, endpoint);
                projectData.put(endpoint, entities);

                MetricsCollector.incrementBy("jsonRecordsLoaded", entities.length());

                // enriquecer PLAN e RUN
                if ("plan".equalsIgnoreCase(endpoint) || "run".equalsIgnoreCase(endpoint)) {
                    releaseEnricher.enrich(entities, releaseMetaById, endpoint);
                }
            }

            // -----------------------------------------------
            // 2 — RELEASE GLOBAL
            // -----------------------------------------------
            globalMetadata.applyGlobal(projectData, releaseMetaById);

            // -----------------------------------------------
            // 3 — CARREGAR RUN_RESULTS
            // -----------------------------------------------
            Map<String, JSONArray> runResults = runResultsLoader.load(project);
            projectData.put("run_results", new JSONObject(runResults));

            consolidated.put(project, projectData);

            LoggerUtils.success("✔ Consolidação do projeto " + project + " concluída.");
        }

        LoggerUtils.success("🏁 Consolidação finalizada.");
        return consolidated;
    }
}
