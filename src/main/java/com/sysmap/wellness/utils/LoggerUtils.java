package com.sysmap.wellness.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Classe utilitária para registro de logs coloridos e legíveis no console.
 * <p>
 * Agora inclui suporte a tempo de execução, progresso percentual e seções nomeadas.
 * </p>
 *
 * @author
 * @version 2.0
 */
public class LoggerUtils {

    // === Códigos ANSI para cores ===
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[37m";

    /** Formato padrão de horário */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Armazena timers ativos (início de medição) */
    private static final Map<String, Long> TIMERS = new ConcurrentHashMap<>();

    private static String now() {
        return "[" + LocalDateTime.now().format(TIME_FMT) + "]";
    }

    public static void divider() {
        System.out.println(CYAN + "-------------------------------------------------------------" + RESET);
    }

    // ===========================================================
    //  LOGS PADRÃO
    // ===========================================================

    public static void info(String msg) {
        System.out.println(now() + " " + BLUE + "[INFO] " + RESET + msg);
    }

    public static void step(String msg) {
        System.out.println(now() + " " + MAGENTA + "[STEP] " + RESET + msg);
    }

    public static void success(String msg) {
        System.out.println(now() + " " + GREEN + "[OK] " + RESET + msg);
    }

    public static void warn(String msg) {
        System.out.println(now() + " " + YELLOW + "[WARN] " + RESET + msg);
    }

    public static void error(String msg) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
    }

    public static void error(String msg, Throwable t) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
        if (t != null) t.printStackTrace(System.out);
    }

    public static void metric(String key, Object value) {
        System.out.println(now() + " " + CYAN + "[METRIC] " + RESET + key + " = " + value);
    }

    // ===========================================================
    //  NOVOS RECURSOS
    // ===========================================================

    /**
     * Marca o início de uma medição de tempo.
     * @param tag Identificador único (ex: "FULLY/result")
     */
    public static void startTimer(String tag) {
        TIMERS.put(tag, System.nanoTime());
    }

    /**
     * Marca o fim de uma medição e mostra a duração no log.
     * @param tag Identificador usado em startTimer()
     * @param message Mensagem a ser exibida junto do tempo
     */
    public static void endTimer(String tag, String message) {
        Long start = TIMERS.remove(tag);
        if (start == null) {
            warn("Timer '" + tag + "' não iniciado.");
            return;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        double elapsedSec = elapsedMs / 1000.0;
        String formatted = elapsedSec >= 1
                ? String.format("%.2fs", elapsedSec)
                : String.format("%dms", elapsedMs);
        System.out.println(now() + " " + CYAN + "[TIME] " + RESET + message + " (" + formatted + ")");
    }

    /**
     * Loga um tempo de execução simples sem precisar de startTimer().
     */
    public static void time(String msg, long startTimeNs) {
        long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000;
        double elapsedSec = elapsedMs / 1000.0;
        String formatted = elapsedSec >= 1
                ? String.format("%.2fs", elapsedSec)
                : String.format("%dms", elapsedMs);
        System.out.println(now() + " " + CYAN + "[TIME] " + RESET + msg + " (" + formatted + ")");
    }

    /**
     * Exibe um progresso percentual (0–100%).
     */
    public static void progress(String context, int current, int total) {
        int percent = total > 0 ? (int) ((current * 100.0) / total) : 0;
        System.out.println(now() + " " + WHITE + "[PROGRESS] " + RESET +
                context + " → " + current + "/" + total + " (" + percent + "%)");
    }

    /**
     * Exibe uma nova seção destacada visualmente.
     */
    public static void section(String title) {
        divider();
        System.out.println(MAGENTA + "====================[ " + title + " ]====================" + RESET);
    }

    /**
     * Loga o tamanho de um arquivo ou payload (em bytes ou KB/MB).
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
        System.out.println(now() + " " + CYAN + "[SIZE] " + RESET + label + " = " + formatted);
    }

    /**
     * Exibe mensagem com tag de origem da classe.
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
