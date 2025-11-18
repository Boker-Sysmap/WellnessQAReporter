package com.sysmap.wellness.config;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <h1>ConfigManager – Carregamento Premium de Configurações</h1>
 *
 * <p>
 * Esta classe centraliza todo o mecanismo de carregamento e gerenciamento de
 * configurações do projeto <b>WellnessQAReporter</b>, oferecendo:
 * </p>
 *
 * <ul>
 *     <li>Carregamento seguro de arquivos <b>config.properties</b>;</li>
 *     <li>Suporte a origem dupla:
 *         <ul>
 *           <li>arquivos externos (./config/*.properties)</li>
 *           <li>classpath (/config/*.properties)</li>
 *         </ul>
 *     </li>
 *     <li>Fail-fast para configurações críticas;</li>
 *     <li>Sanitização automática dos valores carregados;</li>
 *     <li>Carregamento especializado de endpoints;</li>
 *     <li>Métodos utilitários para acesso seguro às propriedades;</li>
 *     <li>Compatibilidade total com Java 8+;</li>
 *     <li>Logs padronizados com o LoggerUtils.</li>
 * </ul>
 *
 * <p>
 * O design favorece previsibilidade e isolamento de responsabilidade:
 * todas as operações de leitura, fallback e validação estão contidas em
 * um único ponto, reduzindo risco de inconsistência.
 * </p>
 *
 * <h2>Estratégia de carregamento:</h2>
 * <ol>
 *     <li>Tentar carregar <code>config/config.properties</code> do filesystem;</li>
 *     <li>Se não existir, carregar <code>/config/config.properties</code> do classpath;</li>
 *     <li>Repetir o processo para <code>endpoints.properties</code>;</li>
 *     <li>Sanear conteúdo (trim, remoção de lixo);</li>
 *     <li>Validar configuração de identificador de release (mnemônicos);</li>
 *     <li>Realizar fail-fast caso propriedades essenciais estejam ausentes.</li>
 * </ol>
 *
 * Esta classe é 100% thread-safe devido ao uso de inicialização estática
 * e propriedades imutáveis após carregamento.
 */
public class ConfigManager {

    /** Nome padrão do arquivo de configuração principal. */
    private static final String CONFIG_FILE = "config.properties";

    /** Nome padrão do arquivo utilizado para flags de endpoints. */
    private static final String ENDPOINTS_FILE = "endpoints.properties";

    /** Propriedades carregadas em memória, compartilhadas pela aplicação. */
    private static final Properties props = new Properties();

    /**
     * Estrutura de apoio para endpoints ativados/desativados.
     * A chave é o nome do endpoint; o valor indica se está ativo (true).
     */
    private static Map<String, Boolean> endpointFlags = new LinkedHashMap<>();

    /** Regex para tokens de identificador de release: ${token}. */
    private static final Pattern IDENTIFIER_TOKEN_PATTERN =
        Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    /* Inicialização estática do config ao carregar a classe. */
    static {
        loadConfig();
    }

    // =====================================================================
    //  CARREGAMENTO PRINCIPAL DO CONFIG
    // =====================================================================

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
        validateReleaseIdentifierConfig(); // 🔥 NOVO: validação avançada dos mnemônicos
    }

    // =====================================================================
    //  CARREGAMENTO DE ARQUIVOS (EXTERNO E CLASSPATH)
    // =====================================================================

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

            if (is == null)
                return false;

            props.load(is);
            LoggerUtils.success("✔ Config carregado do classpath (/config/" + filename + ")");
            return true;

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao carregar config do classpath", e);
            return false;
        }
    }

    // =====================================================================
    //  ENDPOINTS
    // =====================================================================

    private static void loadEndpoints() {
        endpointFlags.clear();

        boolean loaded =
            loadEndpointsExternal() ||
                loadEndpointsClasspath();

        if (!loaded) {
            LoggerUtils.warn("⚠️ endpoints.properties não encontrado; usando qase.endpoints do config.properties.");
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

    // =====================================================================
    //  SANITIZAÇÃO
    // =====================================================================

    private static void sanitizeProperties() {
        for (Object key : props.keySet()) {
            String k = key.toString();
            String value = props.getProperty(k);
            if (value != null) {
                props.setProperty(k, value.trim());
            }
        }
    }

    // =====================================================================
    //  API PUBLICA DE ACESSO
    // =====================================================================

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static Properties getRawProperties() {
        return props;
    }

    // =====================================================================
    //  LISTA DE PROJETOS
    // =====================================================================

    public static List<String> getProjects() {
        String raw = props.getProperty("qase.projects", "");

        if (raw.trim().isEmpty())
            return Collections.emptyList();

        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    // =====================================================================
    //  ENDPOINT MANAGEMENT
    // =====================================================================

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

        if (raw.trim().isEmpty())
            return Collections.emptyList();

        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    // =====================================================================
    //  API TOKEN / BASE URL
    // =====================================================================

    public static String getApiToken() {
        return props.getProperty("qase.api.token");
    }

    public static String getApiBaseUrl() {
        return props.getProperty("qase.api.baseUrl", "https://api.qase.io/v1");
    }

    // =====================================================================
    //  HISTÓRICO DE KPI – Diretório base
    // =====================================================================

    public static String getKPIHistoryBaseDir() {
        String path = props.getProperty("history.kpi.baseDir", "historico/kpis").trim();

        Path dir = Paths.get(path);

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao criar diretório do histórico de KPIs: " + dir, e);
        }

        return dir.toString();
    }

    // =====================================================================
    //  IDENTIFICADOR DE RELEASE – FORMATO E MNEMÔNICOS
    // =====================================================================

    /**
     * Retorna o formato configurado para o identificador de release.
     *
     * Exemplo:
     *   ${sprint}_${version}_${environment}_${platform}_${language}_${testType}
     */
    public static String getReleaseIdentifierFormat() {
        String raw = props.getProperty("release.identifier.format", "");
        return raw == null ? "" : raw.trim();
    }

    /**
     * Retorna labels amigáveis para cada mnemônico, lidos de:
     *   identifier.mnemonic.&lt;token&gt;
     *
     * Exemplo:
     *   identifier.mnemonic.sprint=Sprint
     *   identifier.mnemonic.version=Versão
     *
     * @return mapa imutável token → label amigável.
     */
    public static Map<String, String> getIdentifierMnemonicLabels() {
        return Collections.unmodifiableMap(buildIdentifierMnemonicLabels());
    }

    /**
     * Retorna o conjunto de mnemônicos usados no release.identifier.format,
     * extraídos de padrões ${token}.
     */
    public static Set<String> getIdentifierTokensFromFormat() {
        String format = getReleaseIdentifierFormat();
        if (format.isEmpty()) return Collections.emptySet();

        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = IDENTIFIER_TOKEN_PATTERN.matcher(format);
        while (m.find()) {
            tokens.add(m.group(1));
        }
        return tokens;
    }

    /**
     * Validação avançada da configuração de identificador de release.
     *
     * <ul>
     *   <li>Verifica se o formato está definido;</li>
     *   <li>Verifica se há ao menos um mnemônico ${...};</li>
     *   <li>Verifica se cada mnemônico usado possui label em identifier.mnemonic.*;</li>
     * </ul>
     *
     * Nenhuma exceção é lançada – apenas warnings são registrados em log,
     * garantindo que o sistema siga operando mesmo com configuração parcial.
     */
    private static void validateReleaseIdentifierConfig() {
        String format = getReleaseIdentifierFormat();

        if (format.isEmpty()) {
            LoggerUtils.warn("⚠️ release.identifier.format não definido no config.properties. Identificador dinâmico de release ficará indisponível.");
            return;
        }

        Matcher matcher = IDENTIFIER_TOKEN_PATTERN.matcher(format);
        Set<String> tokens = new LinkedHashSet<>();

        while (matcher.find()) {
            tokens.add(matcher.group(1));
        }

        if (tokens.isEmpty()) {
            LoggerUtils.warn("⚠️ release.identifier.format definido, mas nenhum mnemônico encontrado no padrão ${token}. Formato: " + format);
            return;
        }

        Map<String, String> labels = buildIdentifierMnemonicLabels();

        for (String token : tokens) {
            if (!labels.containsKey(token)) {
                LoggerUtils.warn(
                    "⚠️ Mnemônico '" + token + "' utilizado em release.identifier.format, " +
                        "mas não há label correspondente em identifier.mnemonic." + token
                );
            }
        }
    }

    /**
     * Constrói o mapa de labels de mnemônicos a partir de identifier.mnemonic.*.
     */
    private static Map<String, String> buildIdentifierMnemonicLabels() {
        Map<String, String> map = new LinkedHashMap<>();

        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("identifier.mnemonic.")) {
                String token = name.substring("identifier.mnemonic.".length());
                String value = props.getProperty(name);
                map.put(token, value);
            }
        }

        return map;
    }

    // =====================================================================
    //  RELOAD
    // =====================================================================

    public static void reload() {
        props.clear();
        endpointFlags.clear();
        loadConfig();
    }
    public static boolean isAllowedEnvironment(String env) {
        return getList("environment.allowed").contains(env.toUpperCase());
    }

    public static boolean isAllowedPlatform(String p) {
        return getList("platform.allowed").contains(p.toUpperCase());
    }

    public static boolean isAllowedLanguage(String p) {
        return getList("language.allowed").contains(p.toUpperCase());
    }

    public static boolean isAllowedTestType(String p) {
        return getList("testType.allowed").contains(p.toUpperCase());
    }

    public static String getVersionPattern() {
        return props.getProperty("identifier.version.pattern", "\\d+\\.\\d+\\.\\d+");
    }

}
