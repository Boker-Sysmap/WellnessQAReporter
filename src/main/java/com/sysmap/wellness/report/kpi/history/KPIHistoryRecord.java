package com.sysmap.wellness.report.kpi.history;

import org.json.JSONObject;

import java.time.LocalDateTime;

/**
 * Representa um registro histórico de um KPI calculado para um projeto e release
 * específicos. Cada instância desta classe corresponde a um “snapshot” do valor do
 * KPI no momento em que foi calculado, contendo tanto o valor bruto quanto
 * informações complementares que descrevem ou qualificam o KPI.
 *
 * <p>Este registro é utilizado para:</p>
 * <ul>
 *   <li>Persistência do histórico (via {@code KPIHistoryRepository});</li>
 *   <li>Construção de séries temporais (tendência de KPIs);</li>
 *   <li>Alimentação do Painel Consolidado com valores de releases anteriores;</li>
 *   <li>Auditoria e rastreabilidade da evolução dos KPIs ao longo do tempo.</li>
 * </ul>
 *
 * <p>Os campos principais armazenam informações fundamentais como o nome do KPI,
 * o projeto ao qual pertence, a release onde foi calculado, o timestamp do cálculo,
 * o valor bruto e um conjunto de detalhes adicionais utilizados para formatação,
 * descrição e enriquecimento das métricas.</p>
 *
 * <p>A classe também fornece suporte nativo para serialização e desserialização
 * em JSON, garantindo compatibilidade com o modelo de arquivos persistidos em
 * {@code kpi_results.json}.</p>
 */
public class KPIHistoryRecord {

    private final String kpiName;      // ex: escopo_planejado
    private final String project;      // ex: FULLYREPO
    private final String release;      // ex: FULLYREPO-2025-02-R01
    private final LocalDateTime timestamp;
    private final Object value;        // valor numérico original
    private final JSONObject details;  // JSON com campos extras (name, percent, etc.)

    /**
     * Cria um novo registro histórico de KPI contendo:
     * <ul>
     *   <li>Identificação do KPI;</li>
     *   <li>Projeto relacionado;</li>
     *   <li>Release do ciclo de testes;</li>
     *   <li>Timestamp indicando o momento exato da captura;</li>
     *   <li>Valor bruto (numérico ou textual);</li>
     *   <li>Metadados e detalhes opcionais.</li>
     * </ul>
     *
     * @param kpiName   Nome do KPI.
     * @param project   Nome do projeto.
     * @param release   Release associada.
     * @param timestamp Momento da captura do KPI.
     * @param value     Valor bruto do KPI.
     * @param details   JSON contendo informações complementares.
     */
    public KPIHistoryRecord(
        String kpiName,
        String project,
        String release,
        LocalDateTime timestamp,
        Object value,
        JSONObject details
    ) {
        this.kpiName = kpiName;
        this.project = project;
        this.release = release;
        this.timestamp = timestamp;
        this.value = value;
        this.details = details != null ? details : new JSONObject();
    }

    /**
     * @return Nome interno do KPI (ex.: "escopo_planejado").
     */
    public String getKpiName() {
        return kpiName;
    }

    /**
     * @return Nome do projeto ao qual este registro pertence.
     */
    public String getProject() {
        return project;
    }

    /**
     * Retorna o identificador da release (formato padrão):
     *
     * <pre>
     *   PROJETO-AAAA-MM-RNN
     * </pre>
     *
     * @return Nome da release.
     */
    public String getRelease() {
        return release;
    }

    /**
     * Alias semântico para {@link #getRelease()}.
     * Mantido para consistência com outras camadas do relatório.
     *
     * @return Nome da release associada ao KPI.
     */
    public String getReleaseName() {
        return release;
    }

    /**
     * @return Timestamp indicando quando este KPI foi capturado.
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * @return Valor bruto do KPI, podendo ser número, texto ou outro tipo simples.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Retorna detalhes complementares sobre o KPI, incluindo possíveis campos como:
     * <ul>
     *   <li>{@code name}: Nome amigável;</li>
     *   <li>{@code formattedValue}: Valor formatado para exibição;</li>
     *   <li>{@code trendSymbol}: Símbolo de tendência (“↑”, “↓”, “→”);</li>
     *   <li>{@code description}: Descrição textual;</li>
     *   <li>{@code percent}: Indicador de formato percentual.</li>
     * </ul>
     *
     * @return Objeto JSON contendo metadados do KPI.
     */
    public JSONObject getDetails() {
        return details;
    }

    // =========================================================
    // SERIALIZAÇÃO JSON
    // =========================================================

    /**
     * Serializa este registro histórico para um objeto JSON pronto para persistência.
     *
     * <p>Formato resultante:</p>
     *
     * <pre>
     * {
     *   "kpiName": "...",
     *   "project": "...",
     *   "release": "...",
     *   "timestamp": "2025-02-01T10:35:00",
     *   "value": 42,
     *   "details": { ... }
     * }
     * </pre>
     *
     * @return JSONObject representando este registro.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("kpiName", kpiName);
        json.put("project", project);
        json.put("release", release);
        json.put("timestamp", timestamp.toString());
        json.put("value", value);
        json.put("details", details);
        return json;
    }

    /**
     * Constrói um {@link KPIHistoryRecord} a partir de um objeto JSON previamente
     * serializado. Caso o timestamp não esteja presente ou tenha um formato inválido,
     * o timestamp atual é utilizado como fallback.
     *
     * @param json JSON contendo os campos do registro.
     * @return Instância reconstruída de {@link KPIHistoryRecord}.
     */
    public static KPIHistoryRecord fromJson(JSONObject json) {
        String kpiName = json.optString("kpiName", null);
        String project = json.optString("project", null);
        String release = json.optString("release", null);
        String ts = json.optString("timestamp", null);
        LocalDateTime timestamp = ts != null ? LocalDateTime.parse(ts) : LocalDateTime.now();
        Object value = json.opt("value");
        JSONObject details = json.optJSONObject("details");
        return new KPIHistoryRecord(kpiName, project, release, timestamp, value, details);
    }
}
