package com.sysmap.wellness.config;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gerenciador de configuração do sistema Wellness QA Reporter.
 *
 * <p>Agora busca automaticamente os arquivos de configuração tanto em:
 * <ul>
 *   <li><b>./config/</b> (pasta externa ao lado do JAR)</li>
 *   <li><b>/config/</b> (dentro do classpath, ex: src/main/resources/config)</li>
 * </ul>
 * </p>
 */
public class ConfigManager {

    private static final String CONFIG_FILENAME = "config.properties";
    private static final String ENDPOINTS_FILENAME = "endpoints.properties";

    private static final Properties props = new Properties();
    private static Map<String, Boolean> endpointFlags = null;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Path externalConfig = Paths.get("config", CONFIG_FILENAME);
        Path externalEndpoints = Paths.get("config", ENDPOINTS_FILENAME);

        boolean loaded = false;

        // 1️⃣ Tenta carregar config externa
        if (Files.exists(externalConfig)) {
            try (InputStream is = Files.newInputStream(externalConfig)) {
                props.load(is);
                LoggerUtils.success("✔ Config carregado de " + externalConfig.toAbsolutePath());
                loaded = true;
            } catch (IOException e) {
                LoggerUtils.error("❌ Falha ao ler " + externalConfig, e);
            }
        }

        // 2️⃣ Se não encontrou externamente, tenta carregar do classpath
        if (!loaded) {
            try (InputStream is = ConfigManager.class.getResourceAsStream("/config/" + CONFIG_FILENAME)) {
                if (is != null) {
                    props.load(is);
                    LoggerUtils.success("✔ Config carregado do classpath (/config/config.properties)");
                    loaded = true;
                }
            } catch (IOException e) {
                LoggerUtils.error("❌ Falha ao ler config.properties do classpath", e);
            }
        }

        if (!loaded) {
            LoggerUtils.warn("⚠️ Nenhum config.properties encontrado!");
        }

        // Endpoints
        endpointFlags = loadEndpointFlags(externalEndpoints);
    }

    private static Map<String, Boolean> loadEndpointFlags(Path externalEndpoints) {
        Map<String, Boolean> map = new LinkedHashMap<>();

        boolean loaded = false;

        // 1️⃣ Tenta arquivo externo
        if (Files.exists(externalEndpoints)) {
            try (BufferedReader reader = Files.newBufferedReader(externalEndpoints)) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                map.put(parts[0].trim(), Boolean.parseBoolean(parts[1].trim()));
                            }
                        });
                LoggerUtils.success("✔ Endpoints carregados de " + externalEndpoints.toAbsolutePath());
                loaded = true;
            } catch (IOException e) {
                LoggerUtils.error("❌ Falha ao ler endpoints.properties externo", e);
            }
        }

        // 2️⃣ Se não encontrou externamente, tenta dentro do JAR
        if (!loaded) {
            try (InputStream is = ConfigManager.class.getResourceAsStream("/config/" + ENDPOINTS_FILENAME)) {
                if (is != null) {
                    Properties endpointProps = new Properties();
                    endpointProps.load(is);
                    endpointProps.forEach((k, v) -> map.put(k.toString(), Boolean.parseBoolean(v.toString())));
                    LoggerUtils.success("✔ Endpoints carregados do classpath (/config/endpoints.properties)");
                    loaded = true;
                }
            } catch (IOException e) {
                LoggerUtils.error("❌ Falha ao ler endpoints.properties do classpath", e);
            }
        }

        if (!loaded) {
            LoggerUtils.warn("⚠️ endpoints.properties não encontrado. Usando qase.endpoints do config.properties, se disponível.");
        }

        return map;
    }

    // === Métodos públicos ===

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static List<String> getProjects() {
        String raw = props.getProperty("qase.projects", "");
        if (raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    public static List<String> getEndpoints() {
        if (endpointFlags != null && !endpointFlags.isEmpty()) {
            return new ArrayList<>(endpointFlags.keySet());
        }
        String raw = props.getProperty("qase.endpoints", "");
        if (raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    public static List<String> getActiveEndpoints() {
        if (endpointFlags == null || endpointFlags.isEmpty()) {
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

    public static void reload() {
        props.clear();
        endpointFlags = null;
        loadConfig();
    }
}
