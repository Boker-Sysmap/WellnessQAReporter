package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de alto nível responsável por gerenciar e consultar o histórico
 * de KPIs de cada projeto. Esta classe funciona como a camada de fachada
 * (facade) para o repositório de histórico, encapsulando regras de acesso,
 * conversões e agregações necessárias para alimentar diferentes partes do
 * relatório, como Painel Consolidado, KPIs executivos e análises temporais.
 *
 * <p>Principais responsabilidades:</p>
 * <ul>
 *   <li>Persistir no histórico os KPIs calculados para cada release;</li>
 *   <li>Consultar a última ocorrência de um KPI específico (último snapshot);</li>
 *   <li>Disponibilizar a série temporal completa de um KPI;</li>
 *   <li>Converter registros históricos em {@link KPIData};</li>
 *   <li>Selecionar releases mais recentes e organizar KPIs para o Painel Consolidado;</li>
 *   <li>Oferecer métodos convenientes para recuperar valores numéricos recentes;</li>
 * </ul>
 *
 * <p>Este serviço atua como intermediário entre o {@link KPIHistoryRepository}
 * (armazenamento) e as partes superiores do sistema, especialmente o
 * {@link com.sysmap.wellness.report.service.KPIEngine} e o
 * {@link com.sysmap.wellness.report.ReportGenerator}.</p>
 */
public class KPIHistoryService {

    private final KPIHistoryRepository repository;

    /**
     * Construtor padrão. Inicializa o serviço utilizando uma instância nova
     * de {@link KPIHistoryRepository}.
     */
    public KPIHistoryService() {
        this.repository = new KPIHistoryRepository();
    }

    /**
     * Construtor alternativo permitindo injetar um repositório customizado.
     * Útil para testes unitários ou cenários personalizados.
     *
     * @param repository Instância customizada do repositório de histórico.
     */
    public KPIHistoryService(KPIHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Salva todos os KPIs calculados para um projeto e release no repositório
     * de histórico. Cada KPI é registrado como um {@link KPIHistoryRecord}
     * contendo timestamp, valores numéricos, detalhes e metadados.
     *
     * @param project      Nome do projeto.
     * @param releaseName  Identificador da release relacionada aos KPIs.
     * @param kpis         Lista de KPIs a serem persistidos.
     */
    public void saveAll(String project, String releaseName, List<KPIData> kpis) {
        repository.save(project, releaseName, kpis);
    }

    /**
     * Obtém o registro histórico mais recente de um KPI específico.
     *
     * @param project Nome do projeto.
     * @param kpiKey  Chave única do KPI.
     * @return Optional contendo o último registro, caso exista.
     */
    public Optional<KPIHistoryRecord> getLast(String project, String kpiKey) {
        return repository.loadLast(project, kpiKey);
    }

    /**
     * Obtém a série temporal completa de um KPI, ordenada por timestamp.
     *
     * @param project Nome do projeto.
     * @param kpiKey  Identificador do KPI desejado.
     * @return Lista ordenada de {@link KPIHistoryRecord} para esse KPI.
     */
    public List<KPIHistoryRecord> getTrend(String project, String kpiKey) {
        return repository.loadByKPI(project, kpiKey);
    }

    /**
     * Retorna o último valor numérico registrado para o KPI solicitado.
     * Caso não haja registro ou o valor seja inválido/non-numérico,
     * retorna 0.0.
     *
     * @param project Nome do projeto.
     * @param kpiKey  Identificador do KPI.
     * @return Valor numérico mais recente ou 0.0 se indisponível.
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
    // ---------------------------------------------------------------------

    /**
     * Carrega os KPIs necessários para o Painel Consolidado, respeitando:
     *
     * <ul>
     *   <li>Somente KPIs cujas chaves constam em {@code selectedKpiKeys};</li>
     *   <li>Organização por release, selecionando apenas os registros MAIS RECENTES;</li>
     *   <li>Ordenação de releases do mais recente para o mais antigo;</li>
     *   <li>Limitação opcional de releases via {@code maxReleases};</li>
     *   <li>Conversão de {@link KPIHistoryRecord} para {@link KPIData};</li>
     * </ul>
     *
     * Estrutura retornada:
     * <pre>
     *   [ KPIData(kpiA, releaseX),
     *     KPIData(kpiB, releaseX),
     *     KPIData(kpiA, releaseW),
     *     KPIData(kpiB, releaseW),
     *     ... ]
     * </pre>
     *
     * @param project          Nome do projeto.
     * @param selectedKpiKeys  Lista de KPIs que devem ser exibidos no painel.
     * @param maxReleases      Quantidade máxima de releases (0 = todas).
     * @return Lista linear de KPIData, organizada por release e KPI.
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

        // Filtra apenas os KPIs selecionados
        List<KPIHistoryRecord> filtered = all.stream()
            .filter(r -> selectedSet.contains(r.getKpiName()))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // Agrupa releases → (kpiKey → registro mais recente)
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

        // Releases ordenadas (mais recente primeiro)
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

    /**
     * Converte um {@link KPIHistoryRecord} para um {@link KPIData}, aplicando:
     * <ul>
     *   <li>Extração de nome, valor, formatação e metadados;</li>
     *   <li>Conversão segura para número caso o valor seja textual;</li>
     *   <li>Fallbacks para valores ausentes;</li>
     *   <li>Mapeamento de detalhes adicionais (percentual, símbolos de tendência etc.);</li>
     * </ul>
     *
     * @param rec Registro histórico carregado do repositório.
     * @return KPIData correspondente ao registro.
     */
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
