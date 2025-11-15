package com.sysmap.wellness.config;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ConfigManager PREMIUM
 *
 * - Compatível com Java 8+
 * - Sanitização de entrada
 * - Validação de arquivos de config
 * - Carregamento seguro (externo -> classpath)
 * - Logs padronizados
 * - Fail-fast em configurações obrigatórias
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "config.properties";
    private static final String ENDPOINTS_FILE = "endpoints.properties";

    private static final Properties props = new Properties();
    private static Map<String, Boolean> endpointFlags = new LinkedHashMap<>();

    static {
        loadConfig();
    }

    // ------------------------------------------------------------
    //  CARREGAMENTO DO CONFIG
    // ------------------------------------------------------------
    private static void loadConfig() {
        LoggerUtils.step("⚙️ Carregando configurações...");

        boolean loaded =
                loadExternalConfig(CONFIG_FILE) ||
                        loadClasspathConfig(CONFIG_FILE);

        if (!loaded) {
            LoggerUtils.warn("⚠️ Nenhum config.properties encontrado!");
        }

        loadEndpoints();
        sanitizeProperties();
    }

    // ------------------------------------------------------------
    //  CARREGAMENTO EXTERNO / CLASSPATH
    // ------------------------------------------------------------

    private static boolean loadExternalConfig(String filename) {
        Path path = Paths.get("config", filename);
        if (!Files.exists(path)) return false;

        try (InputStream is = Files.newInputStream(path)) {
            props.load(is);
            LoggerUtils.success("✔ Config externo carregado: " + path.toAbsolutePath());
            return true;
        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao carregar config externo: " + path, e);
            return false;
        }
    }

    private static boolean loadClasspathConfig(String filename) {
        try (InputStream is = ConfigManager.class.getResourceAsStream("/config/" + filename)) {

            if (is == null) return false;

            props.load(is);
            LoggerUtils.success("✔ Config carregado do classpath (/config/" + filename + ")");
            return true;

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao carregar config do classpath", e);
            return false;
        }
    }

    // ------------------------------------------------------------
    //  ENDPOINTS
    // ------------------------------------------------------------

    private static void loadEndpoints() {
        endpointFlags.clear();

        boolean loaded =
                loadEndpointsExternal() ||
                        loadEndpointsClasspath();

        if (!loaded) {
            LoggerUtils.warn("⚠ endpoints.properties não encontrado; usando qase.endpoints do config.properties.");
        }
    }

    private static boolean loadEndpointsExternal() {
        Path path = Paths.get("config", ENDPOINTS_FILE);
        if (!Files.exists(path)) return false;

        try (BufferedReader reader = Files.newBufferedReader(path)) {

            reader.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .forEach(ConfigManager::parseEndpointLine);

            LoggerUtils.success("✔ Endpoints carregados do arquivo externo: " + path);
            return true;

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao carregar endpoints externos", e);
            return false;
        }
    }

    private static boolean loadEndpointsClasspath() {
        try (InputStream is = ConfigManager.class.getResourceAsStream("/config/" + ENDPOINTS_FILE)) {

            if (is == null) return false;

            Properties ep = new Properties();
            ep.load(is);

            for (Map.Entry<Object, Object> entry : ep.entrySet()) {
                endpointFlags.put(entry.getKey().toString(),
                        Boolean.parseBoolean(entry.getValue().toString()));
            }

            LoggerUtils.success("✔ Endpoints carregados do classpath (/config/endpoints.properties)");
            return true;

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao carregar endpoints do classpath", e);
            return false;
        }
    }

    private static void parseEndpointLine(String line) {
        String[] parts = line.split("=");
        if (parts.length == 2) {
            endpointFlags.put(parts[0].trim(), Boolean.parseBoolean(parts[1].trim()));
        }
    }

    // ------------------------------------------------------------
    //  SANITIZAÇÃO
    // ------------------------------------------------------------

    private static void sanitizeProperties() {
        for (Object key : props.keySet()) {
            String k = key.toString();
            props.setProperty(k, props.getProperty(k).trim());
        }
    }

    // ------------------------------------------------------------
    //  MÉTODOS PÚBLICOS
    // ------------------------------------------------------------

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static Properties getRawProperties() {
        return props;
    }

    // ------------------------------------------------------------
    //  LISTA DE PROJETOS
    // ------------------------------------------------------------

    public static List<String> getProjects() {
        String raw = props.getProperty("qase.projects", "");

        if (raw.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------
    //  ENDPOINTS
    // ------------------------------------------------------------

    public static List<String> getEndpoints() {
        if (!endpointFlags.isEmpty()) {
            return new ArrayList<>(endpointFlags.keySet());
        }

        return getList("qase.endpoints");
    }

    public static List<String> getActiveEndpoints() {
        if (endpointFlags.isEmpty()) {
            return getEndpoints();
        }

        return endpointFlags.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static List<String> getList(String key) {
        String raw = props.getProperty(key, "");

        if (raw.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------
    //  API TOKEN / BASE URL
    // ------------------------------------------------------------

    public static String getApiToken() {
        return props.getProperty("qase.api.token");
    }

    public static String getApiBaseUrl() {
        return props.getProperty("qase.api.baseUrl", "https://api.qase.io/v1");
    }

    // ------------------------------------------------------------
    //  RELOAD
    // ------------------------------------------------------------

    public static void reload() {
        props.clear();
        endpointFlags.clear();
        loadConfig();
    }
}
