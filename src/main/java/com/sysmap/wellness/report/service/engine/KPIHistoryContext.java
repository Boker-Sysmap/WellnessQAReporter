package com.sysmap.wellness.report.service.engine;

import java.util.Collections;
import java.util.Set;

/**
 * Contexto de histórico de KPIs para um projeto.
 * Encapsula:
 *  - releases já presentes no histórico
 *  - nome da release mais recente
 */
public class KPIHistoryContext {

    private final Set<String> releasesWithHistory;
    private final String newestReleaseName;

    public KPIHistoryContext(Set<String> releasesWithHistory, String newestReleaseName) {
        this.releasesWithHistory = releasesWithHistory;
        this.newestReleaseName = newestReleaseName;
    }

    public Set<String> getReleasesWithHistory() {
        return Collections.unmodifiableSet(releasesWithHistory);
    }

    public String getNewestReleaseName() {
        return newestReleaseName;
    }

    public boolean hasHistory() {
        return !releasesWithHistory.isEmpty();
    }
}
