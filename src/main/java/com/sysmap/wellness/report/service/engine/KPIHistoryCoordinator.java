package com.sysmap.wellness.report.service.engine;

import com.sysmap.wellness.core.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.service.KPIHistoryService;
import com.sysmap.wellness.report.service.model.KPIData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Coordena o acesso ao histórico de KPIs:
 *  - Carrega histórico existente
 *  - Decide se uma release deve ser recalculada
 *  - Persiste novos registros
 */
public class KPIHistoryCoordinator {

    private final KPIHistoryService historyService = new KPIHistoryService();

    public KPIHistoryContext loadHistory(String project) {
        List<KPIHistoryRecord> history = historyService.getAllHistory(project);

        Set<String> releasesWithHistory =
            history.stream()
                .map(KPIHistoryRecord::getReleaseName)
                .collect(Collectors.toCollection(TreeSet::new));

        String newestRelease = history.stream()
            .map(KPIHistoryRecord::getReleaseName)
            .sorted()
            .reduce((a, b) -> b)
            .orElse(null);

        return new KPIHistoryContext(releasesWithHistory, newestRelease);
    }

    /**
     * Regra: processar se:
     *  - não há histórico ainda; OU
     *  - release ainda não existe; OU
     *  - é a release mais recente (sempre recalculada).
     */
    public boolean shouldProcessRelease(String releaseId, KPIHistoryContext ctx) {
        if (releaseId == null || releaseId.isBlank()) return false;

        if (!ctx.hasHistory()) return true;

        boolean exists = ctx.getReleasesWithHistory().contains(releaseId);
        boolean isNewest =
            ctx.getNewestReleaseName() != null &&
                ctx.getNewestReleaseName().equals(releaseId);

        return !exists || isNewest;
    }

    public void save(String project, String releaseId, List<KPIData> releaseKPIs) {
        if (releaseKPIs == null || releaseKPIs.isEmpty()) return;
        historyService.saveAll(project, releaseId, releaseKPIs);
    }
}
