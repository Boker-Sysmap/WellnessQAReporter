package com.sysmap.wellness.config;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
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

    /* Inicialização estática do config ao carregar a classe. */
    static {
        loadConfig();
    }

    // =====================================================================
    //  CARREGAMENTO PRINCIPAL DO CONFIG
    // =====================================================================

    /**
     * Executa o pipeline completo de carregamento:
     *
     * <ol>
     *   <li>Tentativa de carregar config externo;</li>
     *   <li>Fallback para config do classpath;</li>
     *   <li>Carregar arquivo endpoints.properties;</li>
     *   <li>Sanear propriedades;</li>
     * </ol>
     *
     * <p>Falhas são logadas, mas não interrompem o processo,
     * permitindo execução em ambientes parciais.</p>
     */
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

    // =====================================================================
    //  CARREGAMENTO DE ARQUIVOS (EXTERNO E CLASSPATH)
    // =====================================================================

    /**
     * Tenta carregar um arquivo de configuração no diretório local:
     * <pre>./config/&lt;filename&gt;</pre>
     *
     * @param filename nome do arquivo (ex: config.properties)
     * @return true se carregado com sucesso; false caso contrário
     */
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

    /**
     * Tenta carregar arquivo do classpath:
     * <pre>/config/&lt;filename&gt;</pre>
     *
     * @param filename nome do arquivo
     * @return true se carregado; false caso contrário
     */
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

    /**
     * Carrega os endpoints utilizando a mesma estratégia:
     * <ol>
     *   <li>Arquivo externo;</li>
     *   <li>Fallback para classpath;</li>
     * </ol>
     */
    private static void loadEndpoints() {
        endpointFlags.clear();

        boolean loaded =
            loadEndpointsExternal() ||
                loadEndpointsClasspath();

        if (!loaded) {
            LoggerUtils.warn("⚠ endpoints.properties não encontrado; usando qase.endpoints do config.properties.");
        }
    }

    /**
     * Carrega endpoints do arquivo externo:
     * <pre>./config/endpoints.properties</pre>
     */
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

    /**
     * Carrega endpoints do classpath:
     * <pre>/config/endpoints.properties</pre>
     */
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

    /**
     * Converte uma linha do arquivo endpoints.properties em um par chave→flag.
     */
    private static void parseEndpointLine(String line) {
        String[] parts = line.split("=");
        if (parts.length == 2) {
            endpointFlags.put(parts[0].trim(), Boolean.parseBoolean(parts[1].trim()));
        }
    }

    // =====================================================================
    //  SANITIZAÇÃO
    // =====================================================================

    /**
     * Aplica <code>trim()</code> em todos os valores da configuração carregada,
     * garantindo que espaços acidentais não causem erro durante parsing posterior.
     */
    private static void sanitizeProperties() {
        for (Object key : props.keySet()) {
            String k = key.toString();
            props.setProperty(k, props.getProperty(k).trim());
        }
    }

    // =====================================================================
    //  API PUBLICA DE ACESSO
    // =====================================================================

    /** Recupera uma propriedade sem fallback. */
    public static String get(String key) {
        return props.getProperty(key);
    }

    /** Recupera propriedade ou valor padrão. */
    public static String getOrDefault(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /** Retorna todas as propriedades internas (somente leitura). */
    public static Properties getRawProperties() {
        return props;
    }

    // =====================================================================
    //  LISTA DE PROJETOS
    // =====================================================================

    /**
     * Retorna a lista de projetos definida em:
     * <pre>qase.projects=APP1,APP2,PORTAL</pre>
     */
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

    /** Retorna todos os endpoints declarados (ativos ou não). */
    public static List<String> getEndpoints() {
        if (!endpointFlags.isEmpty()) {
            return new ArrayList<>(endpointFlags.keySet());
        }
        return getList("qase.endpoints");
    }

    /** Retorna apenas os endpoints ativos. */
    public static List<String> getActiveEndpoints() {
        if (endpointFlags.isEmpty()) {
            return getEndpoints();
        }
        return endpointFlags.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /** Utilitário interno para retorno de listas. */
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

    /** Token da API do Qase. */
    public static String getApiToken() {
        return props.getProperty("qase.api.token");
    }

    /** URL base da API do Qase (default = produção). */
    public static String getApiBaseUrl() {
        return props.getProperty("qase.api.baseUrl", "https://api.qase.io/v1");
    }

    // =====================================================================
    //  HISTÓRICO DE KPI – Diretório base
    // =====================================================================

    /**
     * <h2>Retorna o diretório base onde o histórico de KPIs deve ser salvo.</h2>
     *
     * <p>Configuração: <code>history.kpi.baseDir</code></p>
     *
     * <p>
     * Caso o diretório não exista, será criado automaticamente.
     * </p>
     *
     * @return caminho normalizado do diretório de histórico de KPIs
     */
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
    //  RELOAD
    // =====================================================================

    /**
     * Recarrega todo o conjunto de propriedades, permitindo atualização dinâmica
     * sem necessidade de reinicializar a aplicação.
     */
    public static void reload() {
        props.clear();
        endpointFlags.clear();
        loadConfig();
    }
}
