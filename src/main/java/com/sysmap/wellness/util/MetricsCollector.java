package com.sysmap.wellness.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe utilitária responsável por coletar e armazenar métricas simples de execução.
 *
 * <p>Utilizada em todo o projeto <b>WellnessQA</b> para registrar informações
 * quantitativas durante o processamento, como:</p>
 * <ul>
 *   <li>Quantidade de arquivos processados</li>
 *   <li>Total de registros lidos ou salvos</li>
 *   <li>Número de erros ocorridos</li>
 *   <li>Tempos de execução ou nomes de arquivos gerados</li>
 * </ul>
 *
 * <p>As métricas são armazenadas em um {@link ConcurrentHashMap}, garantindo
 * segurança em ambientes multi-thread.</p>
 *
 * <h3>Exemplo de uso:</h3>
 * <pre>{@code
 * MetricsCollector.increment("filesProcessed");
 * MetricsCollector.incrementBy("recordsLoaded", 1200);
 * MetricsCollector.set("lastReportFile", "report_full.xlsx");
 * MetricsCollector.printSummary();
 * }</pre>
 *
 * <p>Os logs são integrados ao {@link LoggerUtils}, exibindo métricas formatadas
 * no console para acompanhamento em tempo real.</p>
 *
 * @author
 * @version 1.0
 */
public class MetricsCollector {

    /** Armazena as métricas registradas durante a execução do sistema. */
    private static final Map<String, Number> metrics = new ConcurrentHashMap<>();

    /**
     * Incrementa em +1 o valor de uma métrica existente ou cria uma nova métrica com valor 1.
     *
     * @param key Nome (chave) da métrica
     */
    public static void increment(String key) {
        metrics.merge(key, 1, (oldVal, newVal) -> oldVal.intValue() + 1);
    }

    /**
     * Incrementa o valor de uma métrica por um valor específico.
     *
     * @param key   Nome (chave) da métrica
     * @param value Valor a ser adicionado à métrica
     */
    public static void incrementBy(String key, int value) {
        metrics.merge(key, value, (oldVal, newVal) -> oldVal.intValue() + newVal.intValue());
    }

    /**
     * Define diretamente o valor de uma métrica, substituindo qualquer valor anterior.
     *
     * <p>Se o valor informado não for numérico, seu {@link Object#hashCode()} será usado
     * como representação numérica para armazenar no mapa interno.</p>
     *
     * @param key   Nome (chave) da métrica
     * @param value Valor a ser atribuído
     */
    public static void set(String key, Object value) {
        metrics.put(key, value instanceof Number ? (Number) value : value.hashCode());
        LoggerUtils.metric(key, value);
    }

    /**
     * Recupera o valor atual de uma métrica registrada.
     *
     * @param key Nome (chave) da métrica
     * @return Valor atual da métrica ou {@code 0} caso não exista
     */
    public static Number get(String key) {
        return metrics.getOrDefault(key, 0);
    }

    /**
     * Remove todas as métricas registradas, limpando completamente o mapa interno.
     */
    public static void clear() {
        metrics.clear();
    }

    /**
     * Reinicia as métricas (equivalente a {@link #clear()}), exibindo uma mensagem
     * de log informando a reinicialização.
     *
     * <p>Compatível com chamadas externas do WellnessQAMain.</p>
     */
    public static void reset() {
        clear();
        LoggerUtils.step("📊 Métricas reiniciadas.");
    }

    /**
     * Exibe um resumo completo de todas as métricas atualmente registradas no console.
     *
     * <p>Utiliza o {@link LoggerUtils} para formatação e exibição padronizada.</p>
     */
    public static void printSummary() {
        LoggerUtils.divider();
        LoggerUtils.info("📈 RESUMO DAS MÉTRICAS:");
        metrics.forEach((k, v) -> LoggerUtils.metric(k, v));
        LoggerUtils.divider();
    }
}
