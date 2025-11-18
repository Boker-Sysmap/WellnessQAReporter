package com.sysmap.wellness.report.service.engine;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

import java.util.*;

/**
 * KPIEngine — versão modular
 *
 * Responsabilidades delegadas para componentes internos:
 * - Detecção de releases      → KPIReleaseDetector
 * - Filtragem por release     → KPIReleaseFilter
 * - Coordenação de histórico  → KPIHistoryCoordinator
 * - Processamento por release → KPIReleaseProcessor
 */
public class KPIEngine {

    private final KPIReleaseDetector releaseDetector = new KPIReleaseDetector();
    private final KPIHistoryCoordinator historyCoordinator = new KPIHistoryCoordinator();
    private final KPIReleaseProcessor releaseProcessor = new KPIReleaseProcessor();
    private final KPIReleaseFilter releaseFilter = new KPIReleaseFilter();

    /**
     * Calcula KPIs para todos os projetos consolidados.
     *
     * @param consolidatedData mapa projeto → consolidated.json
     * @param fallbackRelease  release padrão caso nenhuma seja detectada
     */
    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidatedData,
        String fallbackRelease
    ) {
        Map<String, List<KPIData>> result = new LinkedHashMap<>();

        for (String project : consolidatedData.keySet()) {

            LoggerUtils.info("============================================================");
            LoggerUtils.info("📌 PROCESSANDO PROJETO: " + project);
            LoggerUtils.info("============================================================");

            JSONObject consolidated = consolidatedData.get(project);

            // 1) Detectar releases
            List<String> releases =
                releaseDetector.detectReleases(consolidated, fallbackRelease);

            LoggerUtils.info("🗂 Releases detectadas: " + releases);

            // 2) Carregar contexto de histórico
            KPIHistoryContext historyCtx =
                historyCoordinator.loadHistory(project);

            List<KPIData> allKPIs = new ArrayList<>();

            // 3) Processar cada release
            for (String releaseId : releases) {

                if (!historyCoordinator.shouldProcessRelease(releaseId, historyCtx)) {
                    LoggerUtils.info("⛔ Release congelada: " + releaseId);
                    continue;
                }

                // 4) Filtrar consolidated pela release
                JSONObject filtered =
                    releaseFilter.filter(consolidated, releaseId);

                // 5) Calcular KPIs da release
                List<KPIData> releaseKPIs =
                    releaseProcessor.processRelease(project, releaseId, filtered);

                // 6) Persistir histórico
                historyCoordinator.save(project, releaseId, releaseKPIs);

                allKPIs.addAll(releaseKPIs);
            }

            result.put(project, allKPIs);
        }

        return result;
    }
}
