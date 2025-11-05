package com.sysmap.wellness.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe responsável por coletar métricas simples de execução,
 * como quantidade de arquivos processados, registros lidos, erros etc.
 */
public class MetricsCollector {

    private static final Map<String, Number> metrics = new ConcurrentHashMap<>();

    public static void increment(String key) {
        metrics.merge(key, 1, (oldVal, newVal) -> oldVal.intValue() + 1);
    }

    public static void incrementBy(String key, int value) {
        metrics.merge(key, value, (oldVal, newVal) -> oldVal.intValue() + newVal.intValue());
    }

    public static void set(String key, Object value) {
        metrics.put(key, value instanceof Number ? (Number) value : value.hashCode());
        LoggerUtils.metric(key, value);
    }

    public static Number get(String key) {
        return metrics.getOrDefault(key, 0);
    }

    public static void clear() {
        metrics.clear();
    }

    /** 🔄 Reinicia as métricas (alias para clear, compatível com WellnessQAMain). */
    public static void reset() {
        clear();
        LoggerUtils.step("📊 Métricas reiniciadas.");
    }

    public static void printSummary() {
        LoggerUtils.divider();
        LoggerUtils.info("📈 RESUMO DAS MÉTRICAS:");
        metrics.forEach((k, v) -> LoggerUtils.metric(k, v));
        LoggerUtils.divider();
    }
}
