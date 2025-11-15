package com.sysmap.wellness.utils;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coletor de métricas PREMIUM para o projeto WellnessQA.
 *
 * Mantém compatibilidade com a versão anterior e adiciona:
 *  - registro de tempos (timing)
 *  - timers simples (startTimer/endTimer)
 *  - estatísticas agregadas (count, min, max, avg)
 *  - exportação em JSON
 */
public class MetricsCollector {

    /** Métricas genéricas (contadores, últimos valores, etc.) */
    private static final Map<String, Number> metrics = new ConcurrentHashMap<>();

    /** Timers em andamento: chave → nanoTime de início */
    private static final Map<String, Long> runningTimers = new ConcurrentHashMap<>();

    /** Estatísticas de tempo por chave (timing) */
    private static final Map<String, TimingStats> timingStats = new ConcurrentHashMap<>();

    /**
     * Estrutura interna para armazenar estatísticas de tempo:
     *  - quantidade de amostras
     *  - total em ms
     *  - mínimo
     *  - máximo
     */
    private static class TimingStats {
        private long count;
        private long totalMs;
        private long minMs = Long.MAX_VALUE;
        private long maxMs = Long.MIN_VALUE;

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
    // MÉTODOS BÁSICOS (compatíveis com versão anterior)
    // =====================================================================

    /** Incrementa em +1 o valor de uma métrica. */
    public static void increment(String key) {
        metrics.merge(key, 1, (oldVal, newVal) -> oldVal.intValue() + 1);
    }

    /** Incrementa o valor de uma métrica por um valor específico. */
    public static void incrementBy(String key, int value) {
        metrics.merge(key, value, (oldVal, newVal) -> oldVal.intValue() + newVal.intValue());
    }

    /**
     * Define diretamente o valor de uma métrica, substituindo qualquer valor anterior.
     * Se o valor não for Number, seu hashCode é usado.
     */
    public static void set(String key, Object value) {
        Number stored = (value instanceof Number) ? (Number) value : value.hashCode();
        metrics.put(key, stored);
        LoggerUtils.metric(key, value);
    }

    /** Recupera o valor atual de uma métrica registrada. */
    public static Number get(String key) {
        return metrics.getOrDefault(key, 0);
    }

    /** Remove todas as métricas (mas não zera as estatísticas de tempo). */
    public static void clear() {
        metrics.clear();
        timingStats.clear();
        runningTimers.clear();
    }

    /** Reinicia as métricas e exibe log. */
    public static void reset() {
        clear();
        LoggerUtils.step("📊 Métricas reiniciadas.");
    }

    /** Exibe um resumo das métricas e estatísticas de tempo. */
    public static void printSummary() {
        LoggerUtils.divider();
        LoggerUtils.info("📈 RESUMO DAS MÉTRICAS:");

        // Métricas genéricas
        metrics.forEach((k, v) -> LoggerUtils.metric(k, v));

        // Estatísticas de tempo
        if (!timingStats.isEmpty()) {
            LoggerUtils.info("⏱️ ESTATÍSTICAS DE TEMPO (timing):");
            timingStats.forEach((k, stats) -> {
                LoggerUtils.metric(
                        k,
                        String.format(
                                "count=%d, total=%.2f ms, min=%.2f ms, max=%.2f ms, avg=%.2f ms",
                                stats.getCount(),
                                (double) stats.getTotalMs(),
                                (double) stats.getMinMs(),
                                (double) stats.getMaxMs(),
                                stats.getAvgMs()
                        )
                );
            });
        }

        LoggerUtils.divider();
    }

    // =====================================================================
    // NOVO: MÉTRICAS DE TEMPO (timing)
    // =====================================================================

    /**
     * Registra uma métrica de tempo em milissegundos.
     *
     * - Atualiza o mapa de métricas com o último valor.
     * - Atualiza estatísticas agregadas (count, min, max, avg).
     * - Loga com LoggerUtils.metric.
     *
     * @param key Nome da métrica (ex.: "report.totalMs")
     * @param ms  Tempo em milissegundos
     */
    public static void timing(String key, long ms) {
        // mantém compatibilidade com mapa padrão
        metrics.put(key, ms);

        // atualiza estatísticas
        TimingStats stats = timingStats.computeIfAbsent(key, k -> new TimingStats());
        stats.addSample(ms);

        LoggerUtils.metric(key, ms + " ms");
    }

    /**
     * Versão de conveniência para double.
     */
    public static void timing(String key, double ms) {
        timing(key, (long) ms);
    }

    // =====================================================================
    // NOVO: TIMERS SIMPLES (start / end)
    // =====================================================================

    /**
     * Inicia um timer associado a uma chave.
     * Usado para medir blocos de código de forma simples.
     *
     * Exemplo:
     *   MetricsCollector.startTimer("report");
     *   ...
     *   MetricsCollector.endTimer("report", "Geração de relatório");
     */
    public static void startTimer(String key) {
        runningTimers.put(key, System.nanoTime());
    }

    /**
     * Finaliza um timer iniciado por {@link #startTimer(String)} e
     * registra automaticamente o tempo em milissegundos via {@link #timing(String, long)}.
     *
     * @param key   Chave usada em startTimer
     * @param label Descrição amigável para log (pode ser null)
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
    // NOVO: EXPORTAÇÃO EM JSON
    // =====================================================================

    /**
     * Exporta as métricas e tempos em um JSONObject.
     *
     * Estrutura:
     * {
     *   "metrics": { "key": value, ... },
     *   "timings": {
     *       "chave": {
     *           "count": N,
     *           "totalMs": X,
     *           "minMs": Y,
     *           "maxMs": Z,
     *           "avgMs": W
     *       },
     *       ...
     *   }
     * }
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
