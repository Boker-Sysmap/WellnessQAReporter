package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável por manipular e consultar o histórico dos KPIs,
 * agora incluindo a regra de congelamento de releases:
 * <p>
 * 1) Releases antigas não podem ser atualizadas.
 * 2) Apenas a release mais recente pode receber novos snapshots.
 * 3) Releases inexistentes são registradas normalmente.
 */
public class KPIHistoryService {

    private final KPIHistoryRepository repository;

    public KPIHistoryService() {
        this.repository = new KPIHistoryRepository();
    }

    public KPIHistoryService(KPIHistoryRepository repository) {
        this.repository = repository;
    }

    // ============================================================
    //  NOVA REGRA APLICADA AQUI
    // ============================================================

    /**
     * Salva KPIs calculados para uma release, respeitando:
     * <p>
     * - Release inexistente → cria snapshot.
     * - Release existente e mais recente → atualiza.
     * - Release existente mas NÃO é a mais recente → ignora (release congelada).
     *
     * @param project     Nome do projeto
     * @param releaseName Identificador completo da release
     * @param kpis        Lista de KPIs calculados
     */
    public void saveAll(String project, String releaseName, List<KPIData> kpis) {
        List<KPIHistoryRecord> history = repository.loadAll(project);

        boolean releaseExists = history.stream()
            .anyMatch(r -> r.getReleaseName().equals(releaseName));

        String newest = getNewestRelease(history);

        // Caso 1 — Release nunca registrada → criar snapshot
        if (!releaseExists) {
            repository.save(project, releaseName, kpis);
            log("[KPIHistory] Release nova registrada: " + releaseName);
            return;
        }

        // Caso 2 — Release é a mais recente → atualizar
        if (newest == null || newest.equals(releaseName)) {
            repository.save(project, releaseName, kpis);
            log("[KPIHistory] Release mais recente atualizada: " + releaseName);
            return;
        }

        // Caso 3 — Release antiga → não atualizar
        log("[KPIHistory] Ignorado: tentativa de atualizar release congelada: "
            + releaseName + " (mais recente é " + newest + ")");
    }

    /**
     * Retorna o identificador da release mais recente,
     * baseado na ordenação natural das strings.
     */
    private String getNewestRelease(List<KPIHistoryRecord> history) {
        return history.stream()
            .map(KPIHistoryRecord::getReleaseName)
            .sorted()
            .reduce((a, b) -> b) // pega o último
            .orElse(null);
    }

    // ============================================================
    //  MÉTODOS EXISTENTES — NÃO ALTERADOS
    // ============================================================

    public Optional<KPIHistoryRecord> getLast(String project, String kpiKey) {
        return repository.loadLast(project, kpiKey);
    }

    public List<KPIHistoryRecord> getTrend(String project, String kpiKey) {
        return repository.loadByKPI(project, kpiKey);
    }

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

        List<KPIHistoryRecord> filtered = all.stream()
            .filter(r -> selectedSet.contains(r.getKpiName()))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

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

        List<String> releases = new ArrayList<>(byRelease.keySet());
        releases.sort(Comparator.reverseOrder());

        int limit = (maxReleases > 0)
            ? Math.min(maxReleases, releases.size())
            : releases.size();

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
            } catch (NumberFormatException ignored) {
            }
        }

        String formattedValue = details.has("formattedValue")
            ? details.optString("formattedValue", String.valueOf(valueNum))
            : String.valueOf(valueNum);

        String trendSymbol = details.optString("trendSymbol", "→");
        String description = details.optString("description", name);
        boolean percent = details.optBoolean("percent", false);

        String project = rec.getProject();
        String group = rec.getReleaseName();

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

    private void log(String msg) {
        System.out.println(msg);
    }

    /**
     * Retorna todos os registros de histórico de um projeto.
     * Necessário para o KPIEngine decidir:
     * <p>
     * - Releases que já possuem snapshot (e podem estar congeladas)
     * - Releases que são novas no sistema (e precisam ser processadas)
     * - Qual release é a mais recente no histórico
     *
     * @param project Nome do projeto.
     * @return Lista completa de KPIHistoryRecord.
     */
    public List<KPIHistoryRecord> getAllHistory(String project) {
        return repository.loadAll(project);
    }

}
