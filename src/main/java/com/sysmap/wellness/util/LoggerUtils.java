package com.sysmap.wellness.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilitário simples para logs coloridos e legíveis no console.
 * Compatível com todas as classes do projeto WellnessQA.
 */
public class LoggerUtils {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static String now() {
        return "[" + LocalDateTime.now().format(TIME_FMT) + "]";
    }

    public static void divider() {
        System.out.println(CYAN + "-------------------------------------------------------------" + RESET);
    }

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

    public static void error(String msg, Throwable t) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }

    public static void error(String msg) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
    }

    public static void metric(String key, Object value) {
        System.out.println(now() + " " + CYAN + "[METRIC] " + RESET + key + " = " + value);
    }
}
