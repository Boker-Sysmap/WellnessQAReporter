package com.sysmap.wellness.utils;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coletor centralizado de métricas do WellnessQAReporter.
 *
 * <p>
 * Esta classe unifica o registro de métricas operacionais, estatísticas de duração
 * e contadores gerais utilizados ao longo de todo o pipeline de execução.
 * </p>
 *
 * <p>
 * Recursos principais:
 * </p>
 * <ul>
 *     <li>Contadores simples (<b>increment / incrementBy</b>)</li>
 *     <li>Armazenamento arbitrário de valores (<b>set</b>)</li>
 *     <li>Timers de execução (<b>startTimer / endTimer</b>)</li>
 *     <li>Registro consolidado de tempos (<b>timing</b>) — count/min/max/avg</li>
 *     <li>Exportação estruturada em JSON</li>
 *     <li>Thread-safe via {@link ConcurrentHashMap}</li>
 * </ul>
 *
 * <p>
 * O coletor é amplamente utilizado por:
 * <ul>
 *     <li>{@code QaseClient} – contagem de registros carregados</li>
 *     <li>{@code DataConsolidator} – medições de parsing local</li>
 *     <li>{@code ReportGenerator} – tempos de geração de abas e KPIs</li>
 * </ul>
 * </p>
 *
 * <h3>Exemplo rápido:</h3>
 * <pre>{@code
 * MetricsCollector.increment("apiCalls");
 * MetricsCollector.startTimer("report");
 *
 * gerarRelatorio();
 *
 * MetricsCollector.endTimer("report", "Tempo da geração do relatório");
 *
 * System.out.println(MetricsCollector.toJson().toString(2));
 * }</pre>
 */
public class MetricsCollector {

    /** Armazena métricas genéricas (contadores, valores arbitrários, últimas medições). */
    private static final Map<String, Number> metrics = new ConcurrentHashMap<>();

    /** Timers em andamento, armazenando nanoTime inicial. */
    private static final Map<String, Long> runningTimers = new ConcurrentHashMap<>();

    /** Estatísticas agregadas de tempo por chave. */
    private static final Map<String, TimingStats> timingStats = new ConcurrentHashMap<>();

    /**
     * Estrutura interna para estatísticas de tempo:
     * <ul>
     *     <li>count — número de amostras</li>
     *     <li>totalMs — tempo total somado</li>
     *     <li>minMs — menor tempo registrado</li>
     *     <li>maxMs — maior tempo registrado</li>
     *     <li>avgMs — média calculada</li>
     * </ul>
     *
     * <p>
     * Cada operação time-based alimenta esta estrutura,
     * garantindo visão histórica e agregada do consumo de tempo.
     * </p>
     */
    private static class TimingStats {
        private long count;
        private long totalMs;
        private long minMs = Long.MAX_VALUE;
        private long maxMs = Long.MIN_VALUE;

        /**
         * Adiciona uma nova amostra de tempo à estatística agregada.
         *
         * @param ms tempo em milissegundos
         */
        synchronized void addSample(long ms) {
            count++;
            totalMs += ms;
            if (ms < minMs) minMs = ms;
            if (ms > maxMs) maxMs = ms;
        }

        long getCount() { return count; }
        long getTotalMs() { return totalMs; }
        long getMinMs() { return count == 0 ? 0 : minMs; }
        long getMaxMs() { return count == 0 ? 0 : maxMs; }
        double getAvgMs() { return count == 0 ? 0.0 : (double) totalMs / (double) count; }

        /**
         * Retorna uma representação JSON da estatística de tempos.
         */
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            o.put("count", count);
            o.put("totalMs", totalMs);
            o.put("minMs", getMinMs());
            o.put("maxMs", getMaxMs());
            o.put("avgMs", getAvgMs());
            return o;
        }
    }

    // =====================================================================
    // MÉTODOS BÁSICOS — compatíveis com versões anteriores
    // =====================================================================

    /**
     * Incrementa em +1 o valor de uma métrica.
     *
     * @param key Nome da métrica
     */
    public static void increment(String key) {
        metrics.merge(key, 1, (oldVal, newVal) -> oldVal.intValue() + 1);
    }

    /**
     * Incrementa a métrica por um valor arbitrário.
     *
     * @param key Nome da métrica
     * @param value Valor a ser incrementado
     */
    public static void incrementBy(String key, int value) {
        metrics.merge(key, value,
            (oldVal, newVal) -> oldVal.intValue() + newVal.intValue());
    }

    /**
     * Define diretamente o valor da métrica.
     *
     * <p>Se o valor não for um {@code Number}, utiliza {@code hashCode()}.</p>
     *
     * @param key Nome da métrica
     * @param value Valor a ser armazenado
     */
    public static void set(String key, Object value) {
        Number stored = (value instanceof Number) ? (Number) value : value.hashCode();
        metrics.put(key, stored);
        LoggerUtils.metric(key, value);
    }

    /**
     * Retorna o valor atual de uma métrica, ou 0 caso não exista.
     *
     * @param key Nome da métrica
     */
    public static Number get(String key) {
        return metrics.getOrDefault(key, 0);
    }

    /**
     * Remove todas as métricas e estatísticas de tempo.
     */
    public static void clear() {
        metrics.clear();
        timingStats.clear();
        runningTimers.clear();
    }

    /**
     * Reinicia o coletor e exibe registro no log.
     */
    public static void reset() {
        clear();
        LoggerUtils.step("📊 Métricas reiniciadas.");
    }

    /**
     * Exibe no console um resumo consolidado das métricas e timings registrados.
     */
    public static void printSummary() {
        LoggerUtils.divider();
        LoggerUtils.info("📈 RESUMO DAS MÉTRICAS:");

        metrics.forEach(LoggerUtils::metric);

        if (!timingStats.isEmpty()) {
            LoggerUtils.info("⏱️ ESTATÍSTICAS DE TEMPO (timing):");
            timingStats.forEach((k, stats) -> LoggerUtils.metric(
                k,
                String.format(
                    "count=%d, total=%.2f ms, min=%.2f ms, max=%.2f ms, avg=%.2f ms",
                    stats.getCount(),
                    (double) stats.getTotalMs(),
                    (double) stats.getMinMs(),
                    (double) stats.getMaxMs(),
                    stats.getAvgMs()
                )
            ));
        }
        LoggerUtils.divider();
    }

    // =====================================================================
    // MÉTRICAS DE TEMPO (timing)
    // =====================================================================

    /**
     * Registra um valor de tempo e atualiza estatísticas agregadas da chave.
     *
     * <p>
     * Além de atualizar o valor mais recente, mantém histórico para
     * cálculo de min/max/avg.
     * </p>
     *
     * @param key Nome da métrica
     * @param ms Tempo em milissegundos
     */
    public static void timing(String key, long ms) {
        metrics.put(key, ms);

        TimingStats stats = timingStats.computeIfAbsent(key, k -> new TimingStats());
        stats.addSample(ms);

        LoggerUtils.metric(key, ms + " ms");
    }

    /**
     * Versão de conveniência para double.
     *
     * @param key Nome da métrica
     * @param ms Tempo em milissegundos
     */
    public static void timing(String key, double ms) {
        timing(key, (long) ms);
    }

    // =====================================================================
    // TIMERS (start / end)
    // =====================================================================

    /**
     * Inicia um timer associado à chave especificada.
     *
     * <p>Exemplo:</p>
     * <pre>{@code
     * MetricsCollector.startTimer("download");
     * processar();
     * MetricsCollector.endTimer("download", "Processamento finalizado");
     * }</pre>
     *
     * @param key Identificador do timer
     */
    public static void startTimer(String key) {
        runningTimers.put(key, System.nanoTime());
    }

    /**
     * Finaliza o timer e registra o tempo decorrido automaticamente.
     *
     * @param key Chave usada no {@link #startTimer(String)}
     * @param label Texto amigável para logging
     */
    public static void endTimer(String key, String label) {
        Long startNs = runningTimers.remove(key);
        if (startNs == null) {
            LoggerUtils.warn("⚠️ endTimer chamado para chave não iniciada: " + key);
            return;
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        if (label != null && !label.isEmpty()) {
            LoggerUtils.step(String.format("⏱️ %s: %d ms", label, elapsedMs));
        }

        timing(key + ".timeMs", elapsedMs);
    }

    // =====================================================================
    // EXPORTAÇÃO EM JSON
    // =====================================================================

    /**
     * Exporta todas as métricas e estatísticas de tempo em formato JSON.
     *
     * <p>Estrutura retornada:</p>
     * <pre>{@code
     * {
     *   "metrics": {
     *       "apiCalls": 120,
     *       "recordsLoaded": 8742,
     *       ...
     *   },
     *   "timings": {
     *       "report.totalMs": {
     *           "count": 4,
     *           "totalMs": 3000,
     *           "minMs": 650,
     *           "maxMs": 900,
     *           "avgMs": 750
     *       }
     *   }
     * }
     * }</pre>
     *
     * @return JSONObject contendo métricas e tempos agregados
     */
    public static JSONObject toJson() {
        JSONObject root = new JSONObject();

        JSONObject metricsJson = new JSONObject();
        metrics.forEach(metricsJson::put);

        JSONObject timingsJson = new JSONObject();
        timingStats.forEach((k, stats) -> timingsJson.put(k, stats.toJson()));

        root.put("metrics", metricsJson);
        root.put("timings", timingsJson);

        return root;
    }
}
