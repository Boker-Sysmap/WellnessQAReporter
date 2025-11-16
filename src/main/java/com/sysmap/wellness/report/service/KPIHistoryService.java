package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de alto nível para acesso ao histórico de KPIs.
 */
public class KPIHistoryService {

    private final KPIHistoryRepository repository;

    public KPIHistoryService() {
        this.repository = new KPIHistoryRepository();
    }

    public KPIHistoryService(KPIHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Salva todos os KPIs calculados no histórico.
     */
    public void saveAll(String project, String releaseName, List<KPIData> kpis) {
        repository.save(project, releaseName, kpis);
    }

    /**
     * Retorna o último registro histórico de um KPI para um projeto.
     */
    public Optional<KPIHistoryRecord> getLast(String project, String kpiKey) {
        return repository.loadLast(project, kpiKey);
    }

    /**
     * Retorna a série histórica completa de um KPI para um projeto.
     */
    public List<KPIHistoryRecord> getTrend(String project, String kpiKey) {
        return repository.loadByKPI(project, kpiKey);
    }

    /**
     * Retorna o último valor numérico do KPI.
     */
    public double getLastValueAsDouble(String project, String kpiKey) {
        return repository.loadLast(project, kpiKey)
            .map(record -> {
                Object val = record.getValue();
                if (val == null) return 0.0;
                try {
                    return Double.parseDouble(String.valueOf(val));
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            })
            .orElse(0.0);
    }

    // ---------------------------------------------------------------------
    // NOVO: fornece dados prontos para o Painel Consolidado
    // Uma linha por release, multi-KPI, respeitando maxReleases
    // ---------------------------------------------------------------------
    public List<KPIData> loadForPanel(
        String project,
        List<String> selectedKpiKeys,
        int maxReleases
    ) {
        List<KPIHistoryRecord> all = repository.loadAll(project);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> selectedSet = new HashSet<>(selectedKpiKeys);

        // Filtra apenas os KPIs selecionados
        List<KPIHistoryRecord> filtered = all.stream()
            .filter(r -> selectedSet.contains(r.getKpiName()))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // Agrupa por release -> (kpiKey -> registro MAIS RECENTE)
        Map<String, Map<String, KPIHistoryRecord>> byRelease = new HashMap<>();

        for (KPIHistoryRecord r : filtered) {
            String release = r.getReleaseName();
            String key = r.getKpiName();

            byRelease
                .computeIfAbsent(release, k -> new HashMap<>())
                .merge(
                    key,
                    r,
                    (oldVal, newVal) ->
                        newVal.getTimestamp().isAfter(oldVal.getTimestamp()) ? newVal : oldVal
                );
        }

        // Ordena releases DESC (mais recente primeiro)
        List<String> releases = new ArrayList<>(byRelease.keySet());
        releases.sort(Comparator.reverseOrder());

        int limit = (maxReleases > 0)
            ? Math.min(maxReleases, releases.size())
            : releases.size(); // 0 = todas

        List<KPIData> result = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            String release = releases.get(i);
            Map<String, KPIHistoryRecord> perKpi = byRelease.get(release);

            for (String kpiKey : selectedKpiKeys) {
                KPIHistoryRecord rec = perKpi.get(kpiKey);
                if (rec == null) continue;

                KPIData data = convertRecordToKPIData(rec);
                result.add(data);
            }
        }

        return result;
    }

    private KPIData convertRecordToKPIData(KPIHistoryRecord rec) {

        JSONObject details = rec.getDetails() != null ? rec.getDetails() : new JSONObject();

        String key = rec.getKpiName();
        String name = details.optString("name", key);

        double valueNum = 0.0;
        Object raw = rec.getValue();
        if (raw instanceof Number) {
            valueNum = ((Number) raw).doubleValue();
        } else if (raw != null) {
            try {
                valueNum = Double.parseDouble(String.valueOf(raw));
            } catch (NumberFormatException ignored) {}
        }

        String formattedValue = details.has("formattedValue")
            ? details.optString("formattedValue", String.valueOf(valueNum))
            : String.valueOf(valueNum);

        String trendSymbol = details.optString("trendSymbol", "→");
        String description = details.optString("description", name);
        boolean percent = details.optBoolean("percent", false);

        String project = rec.getProject();
        String group = rec.getReleaseName(); // usado como releaseId no relatório

        return new KPIData(
            key,
            name,
            valueNum,
            formattedValue,
            trendSymbol,
            description,
            percent,
            project,
            group
        );
    }
}
