package com.sysmap.wellness.config;

import com.sysmap.wellness.util.LoggerUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ConfigManager: lê config.properties e endpoints.properties.
 *
 * - config/config.properties  - token, baseUrl, projects (CSV)
 * - config/endpoints.properties - lista de endpoints (chave=boolean)
 *
 * Prioridades:
 *   1️⃣ endpoints.properties define endpoints ativos/inativos.
 *   2️⃣ se não existir, usa qase.endpoints em config.properties (CSV).
 */
public class ConfigManager {

    private static final Path CONFIG_DIR = Paths.get("src", "main", "resources", "config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    private static final Path ENDPOINTS_FILE = CONFIG_DIR.resolve("endpoints.properties");

    private static final Properties props = new Properties();
    private static Map<String, Boolean> endpointFlags = null;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        // carrega config.properties se existir
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream is = Files.newInputStream(CONFIG_FILE)) {
                props.load(is);
                LoggerUtils.success("✔ Config loaded from " + CONFIG_FILE.toString());
            } catch (IOException e) {
                LoggerUtils.error("Failed to load config.properties: " + CONFIG_FILE.toString(), e);
            }
        } else {
            LoggerUtils.warn("⚠️ config.properties not found at " + CONFIG_FILE.toString());
        }

        // carrega endpoints.properties (se existir)
        endpointFlags = loadEndpointFlags();
    }

    /**
     * Carrega endpoints.properties no formato:
     * endpoint=true/false (ignora linhas com # ou vazias)
     */
    private static Map<String, Boolean> loadEndpointFlags() {
        Map<String, Boolean> map = new LinkedHashMap<>();

        if (!Files.exists(ENDPOINTS_FILE)) {
            LoggerUtils.warn("⚠️ endpoints.properties not found, using qase.endpoints from config.properties if present.");
            return map;
        }

        try (BufferedReader reader = Files.newBufferedReader(ENDPOINTS_FILE)) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split("=");
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            boolean enabled = Boolean.parseBoolean(parts[1].trim());
                            map.put(key, enabled);
                        }
                    });
            LoggerUtils.success("✔ Endpoints loaded from " + ENDPOINTS_FILE.toString() + " (" + map.size() + ")");
        } catch (IOException e) {
            LoggerUtils.error("Failed to read endpoints.properties: " + ENDPOINTS_FILE.toString(), e);
        }

        return map;
    }

    // === Public getters ===

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * Projects listed as CSV in config.properties: qase.projects=FULLY,CHUBB
     */
    public static List<String> getProjects() {
        String raw = props.getProperty("qase.projects", "");
        if (raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Endpoints list (fallback only if endpoints.properties missing)
     */
    public static List<String> getEndpoints() {
        if (endpointFlags != null && !endpointFlags.isEmpty()) {
            return endpointFlags.keySet().stream().collect(Collectors.toList());
        }

        String raw = props.getProperty("qase.endpoints", "");
        if (raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Retorna apenas os endpoints marcados como "true" em endpoints.properties
     */
    public static List<String> getActiveEndpoints() {
        if (endpointFlags == null || endpointFlags.isEmpty()) {
            // fallback para qase.endpoints
            return getEndpoints();
        }

        return endpointFlags.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public static String getApiToken() {
        return props.getProperty("qase.api.token");
    }

    public static String getApiBaseUrl() {
        return props.getProperty("qase.api.baseUrl", "https://api.qase.io/v1");
    }

    /** força recarregar (útil para testes) */
    public static void reload() {
        props.clear();
        endpointFlags = null;
        loadConfig();
    }
}
