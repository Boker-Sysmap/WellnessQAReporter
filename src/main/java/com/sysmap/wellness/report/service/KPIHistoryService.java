package com.sysmap.wellness.report.service;

import com.sysmap.wellness.core.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.core.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável por manipular e consultar o histórico dos KPIs.
 *
 * <p>Esta classe encapsula o acesso ao {@link KPIHistoryRepository} e provê
 * operações para:
 * <ul>
 *   <li>Salvar snapshots de KPIs por projeto e release;</li>
 *   <li>Recuperar o último valor registrado de um KPI;</li>
 *   <li>Construir séries históricas (trend) para análise de evolução;</li>
 *   <li>Preparar dados consolidados para exibição em painéis.</li>
 * </ul>
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
    //  PERSISTÊNCIA DE HISTÓRICO
    // ============================================================

    /**
     * Salva os KPIs calculados para uma determinada release de um projeto.
     *
     * <p>Cada chamada registra um snapshot do estado atual dos KPIs no
     * repositório de histórico. A forma como esses snapshots são agregados,
     * versionados ou exibidos é responsabilidade das camadas superiores
     * (por exemplo, serviços de painel ou relatórios).</p>
     *
     * @param project     Nome do projeto.
     * @param releaseName Identificador completo da release.
     * @param kpis        Lista de KPIs calculados para a release.
     */
    public void saveAll(String project, String releaseName, List<KPIData> kpis) {
        repository.save(project, releaseName, kpis);
        log("[KPIHistory] Snapshot salvo para release '" + releaseName + "' do projeto '" + project + "'");
    }

    // ============================================================
    //  CONSULTAS DE HISTÓRICO
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

    /**
     * Carrega e consolida os KPIs a serem exibidos em um painel,
     * considerando um conjunto de chaves de KPI e um limite máximo
     * de releases a serem retornadas.
     *
     * <p>Para cada combinação (release, kpiKey), apenas o registro
     * mais recente é considerado.</p>
     *
     * @param project         Nome do projeto.
     * @param selectedKpiKeys Lista de chaves de KPIs a serem consideradas.
     * @param maxReleases     Quantidade máxima de releases a retornar
     *                        (ordem decrescente). Se &lt;= 0, retorna todas.
     * @return Lista de {@link KPIData} prontos para exibição em painel.
     */
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
     *
     * <p>Útil para análises em batch, geração de relatórios históricos
     * ou para serviços que precisem montar visões agregadas a partir
     * de todos os snapshots persistidos.</p>
     *
     * @param project Nome do projeto.
     * @return Lista completa de {@link KPIHistoryRecord}.
     */
    public List<KPIHistoryRecord> getAllHistory(String project) {
        return repository.loadAll(project);
    }

}
