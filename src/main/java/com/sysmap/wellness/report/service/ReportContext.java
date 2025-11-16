package com.sysmap.wellness.report.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Armazena dados temporários para passar informações
 * entre serviços (ex.: releaseByProject).
 */
public class ReportContext {

    private static final Map<String, Object> context = new HashMap<>();

    public static void store(String key, Object value) {
        context.put(key, value);
    }

    public static Object get(String key) {
        return context.get(key);
    }

    public static Map<String, Object> all() {
        return context;
    }

    public static void clear() {
        context.clear();
    }
}
