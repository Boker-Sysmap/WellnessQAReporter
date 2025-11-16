package com.sysmap.wellness.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Utilitário centralizado para emissão de logs padronizados, coloridos e legíveis no console.
 *
 * <p>Fornece recursos avançados de logging para o pipeline do WellnessQAReporter,
 * incluindo:</p>
 *
 * <ul>
 *     <li>Logs em múltiplos níveis (INFO, STEP, OK, WARN, ERROR)</li>
 *     <li>Mensagens timestamped no padrão HH:mm:ss</li>
 *     <li>Medição de tempo (timers iniciáveis por tag)</li>
 *     <li>Progresso percentual para long-running tasks</li>
 *     <li>Logs por seção (visualmente destacados)</li>
 *     <li>Registro de tamanho de payloads (KB/MB)</li>
 *     <li>Logs anotados com origem da classe</li>
 * </ul>
 *
 * <p>Todos os logs utilizam códigos ANSI para cores, garantindo alta legibilidade
 * em consoles modernos (Bash, PowerShell, IntelliJ console, etc.).</p>
 *
 * <p>Esta classe é completamente thread-safe para operações de medição de tempo,
 * graças ao uso de {@link ConcurrentHashMap}.</p>
 *
 * @version 2.0
 */
public class LoggerUtils {

    // ===== Códigos ANSI para cores ================================

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[37m";

    /** Formato padrão de timestamps: HH:mm:ss */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Armazena os timers iniciados por tag */
    private static final Map<String, Long> TIMERS = new ConcurrentHashMap<>();

    /** Obtém o horário atual formatado para prefixar logs. */
    private static String now() {
        return "[" + LocalDateTime.now().format(TIME_FMT) + "]";
    }

    /**
     * Exibe uma linha divisória simples, usada para separar blocos de logs.
     */
    public static void divider() {
        System.out.println(CYAN + "-------------------------------------------------------------" + RESET);
    }

    // ===========================================================
    //  NÍVEIS DE LOG PADRONIZADOS
    // ===========================================================

    /**
     * Log de informação geral.
     *
     * @param msg Mensagem informativa
     */
    public static void info(String msg) {
        System.out.println(now() + " " + BLUE + "[INFO] " + RESET + msg);
    }

    /**
     * Marca o início ou execução de um passo relevante do fluxo.
     *
     * @param msg Mensagem descritiva do passo
     */
    public static void step(String msg) {
        System.out.println(now() + " " + MAGENTA + "[STEP] " + RESET + msg);
    }

    /**
     * Log de sucesso, normalmente usado após conclusão de um bloco ou operação.
     *
     * @param msg Mensagem de conclusão
     */
    public static void success(String msg) {
        System.out.println(now() + " " + GREEN + "[OK] " + RESET + msg);
    }

    /**
     * Log de aviso indicando comportamento suspeito, mas não fatal.
     *
     * @param msg Mensagem de alerta
     */
    public static void warn(String msg) {
        System.out.println(now() + " " + YELLOW + "[WARN] " + RESET + msg);
    }

    /**
     * Log de erro sem stacktrace.
     *
     * @param msg Mensagem de erro
     */
    public static void error(String msg) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
    }

    /**
     * Log de erro com stacktrace, preservando contexto.
     *
     * @param msg Mensagem do erro
     * @param t Exceção opcional lançada durante a operação
     */
    public static void error(String msg, Throwable t) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }

    /**
     * Registra uma métrica arbitrária no console.
     *
     * @param key Nome da métrica
     * @param value Valor associado
     */
    public static void metric(String key, Object value) {
        System.out.println(now() + " " + CYAN + "[METRIC] " + RESET + key + " = " + value);
    }

    // ===========================================================
    //  SUPORTE A MEDIÇÃO DE TEMPO
    // ===========================================================

    /**
     * Inicia um timer identificado por uma tag.
     *
     * <p>Exemplo:</p>
     * <pre>{@code
     * LoggerUtils.startTimer("FULLY/result");
     * ...
     * LoggerUtils.endTimer("FULLY/result", "Coleta de results concluída");
     * }</pre>
     *
     * @param tag Nome único que identifica a medição
     */
    public static void startTimer(String tag) {
        TIMERS.put(tag, System.nanoTime());
    }

    /**
     * Finaliza um timer iniciado via {@link #startTimer(String)}
     * e exibe o tempo decorrido.
     *
     * @param tag Identificador usado no startTimer()
     * @param message Mensagem exibida junto ao tempo
     */
    public static void endTimer(String tag, String message) {
        Long start = TIMERS.remove(tag);
        if (start == null) {
            warn("Timer '" + tag + "' não iniciado.");
            return;
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        double sec = elapsedMs / 1000.0;

        String formatted = sec >= 1
            ? String.format("%.2fs", sec)
            : String.format("%dms", elapsedMs);

        System.out.println(now() + " " + CYAN + "[TIME] " + RESET + message + " (" + formatted + ")");
    }

    /**
     * Medição de tempo direta sem necessidade de armazenar tag/timer.
     *
     * @param msg Mensagem que contextualiza a medição
     * @param startTimeNs Tempo inicial em nanosegundos
     */
    public static void time(String msg, long startTimeNs) {
        long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        double sec = elapsedMs / 1000.0;

        String formatted = sec >= 1
            ? String.format("%.2fs", sec)
            : String.format("%dms", elapsedMs);

        System.out.println(now() + " " + CYAN + "[TIME] " + RESET + msg + " (" + formatted + ")");
    }

    // ===========================================================
    //  PROGRESSO E SEÇÕES VISUAIS
    // ===========================================================

    /**
     * Exibe progresso percentual com contador detalhado.
     *
     * <p>Ideal para loops longos, como coleta paginada da API.</p>
     *
     * @param context Nome da operação
     * @param current Valor atual
     * @param total Valor máximo
     */
    public static void progress(String context, int current, int total) {
        int percent = total > 0 ? (int) ((current * 100.0) / total) : 0;

        System.out.println(now() + " " + WHITE + "[PROGRESS] " + RESET +
            context + " → " + current + "/" + total + " (" + percent + "%)");
    }

    /**
     * Exibe uma seção visualmente destacada.
     *
     * @param title Título da seção
     */
    public static void section(String title) {
        divider();
        System.out.println(MAGENTA +
            "====================[ " + title + " ]====================" +
            RESET);
    }

    // ===========================================================
    //  UTILIDADES COMPLEMENTARES
    // ===========================================================

    /**
     * Loga um valor de tamanho (bytes/KB/MB) com formatação automática.
     *
     * @param label Rótulo do valor
     * @param bytes Quantidade de bytes
     */
    public static void size(String label, long bytes) {
        String formatted;

        if (bytes < 1024) {
            formatted = bytes + " B";
        } else if (bytes < 1024 * 1024) {
            formatted = String.format("%.1f KB", bytes / 1024.0);
        } else {
            formatted = String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }

        System.out.println(now() + " " + CYAN + "[SIZE] " + RESET +
            label + " = " + formatted);
    }

    /**
     * Loga uma mensagem incluindo uma tag de origem (classe ou módulo chamador).
     *
     * @param source Nome da origem (ex: QaseClient, ReportGenerator)
     * @param level Nível desejado (INFO, OK, WARN, ERROR, STEP)
     * @param msg Mensagem a ser exibida
     */
    public static void logWithSource(String source, String level, String msg) {
        String color;

        switch (level.toUpperCase()) {
            case "OK": color = GREEN; break;
            case "STEP": color = MAGENTA; break;
            case "WARN": color = YELLOW; break;
            case "ERROR": color = RED; break;
            default: color = BLUE;
        }

        System.out.println(now() + " " + color + "[" + level + "]" + RESET +
            " [" + source + "] " + msg);
    }
}
