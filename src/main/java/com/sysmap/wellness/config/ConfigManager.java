package com.sysmap.wellness.config;

import com.sysmap.wellness.util.LoggerUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gerenciador de configuração do sistema Wellness QA Reporter.
 *
 * <p>Responsável por carregar e disponibilizar as configurações definidas
 * nos arquivos <b>config.properties</b> e <b>endpoints.properties</b>.
 * Os arquivos são esperados no diretório {@code src/main/resources/config}.</p>
 *
 * <ul>
 *     <li><b>config/config.properties</b> — contém chaves como {@code qase.api.token},
 *         {@code qase.api.baseUrl}, {@code qase.projects} (CSV) e {@code qase.endpoints}.</li>
 *     <li><b>config/endpoints.properties</b> — define endpoints individuais
 *         no formato {@code endpoint=true|false}, permitindo habilitar/desabilitar cada um.</li>
 * </ul>
 *
 * <p><b>Regras de prioridade:</b></p>
 * <ol>
 *     <li>Se existir {@code endpoints.properties}, ele define os endpoints ativos/inativos.</li>
 *     <li>Se não existir, o sistema usa {@code qase.endpoints} do {@code config.properties}.</li>
 * </ol>
 *
 * <p>As propriedades são carregadas automaticamente na inicialização da classe.</p>
 *
 * @author Roberto
 * @version 1.1
 * @since 1.0
 */
public class ConfigManager {

    /** Diretório base onde os arquivos de configuração estão armazenados. */
    private static final Path CONFIG_DIR = Paths.get("src", "main", "resources", "config");

    /** Caminho completo para o arquivo principal de configuração. */
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    /** Caminho completo para o arquivo de definição de endpoints. */
    private static final Path ENDPOINTS_FILE = CONFIG_DIR.resolve("endpoints.properties");

    /** Objeto Properties contendo as configurações gerais. */
    private static final Properties props = new Properties();

    /** Mapa com o estado (ativo/inativo) dos endpoints definidos em endpoints.properties. */
    private static Map<String, Boolean> endpointFlags = null;

    // Bloco de inicialização estático: executado uma vez ao carregar a classe.
    static {
        loadConfig();
    }

    /**
     * Carrega o conteúdo de {@code config.properties} e {@code endpoints.properties}.
     * Caso algum dos arquivos não exista, o sistema apenas registra um aviso.
     */
    private static void loadConfig() {
        // Carrega config.properties
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

        // Carrega endpoints.properties, se existir
        endpointFlags = loadEndpointFlags();
    }

    /**
     * Lê o arquivo {@code endpoints.properties}, que define endpoints ativos e inativos
     * no formato {@code endpoint=true|false}. Linhas em branco ou iniciadas por {@code #} são ignoradas.
     *
     * @return um mapa contendo o nome do endpoint como chave e seu status (true/false) como valor.
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

    // === Métodos públicos de acesso ===

    /**
     * Retorna o valor de uma chave de configuração.
     *
     * @param key nome da propriedade.
     * @return valor da propriedade ou {@code null} se não existir.
     */
    public static String get(String key) {
        return props.getProperty(key);
    }

    /**
     * Retorna o valor de uma chave de configuração, com valor padrão caso não exista.
     *
     * @param key          nome da propriedade.
     * @param defaultValue valor padrão a ser retornado se a chave não for encontrada.
     * @return valor da propriedade ou o valor padrão informado.
     */
    public static String getOrDefault(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * Retorna a lista de projetos configurados na chave {@code qase.projects},
     * separados por vírgula.
     *
     * <p>Exemplo de configuração:
     * <pre>qase.projects=FULLY,CHUBB</pre></p>
     *
     * @return lista de nomes de projetos ou uma lista vazia se a chave não estiver presente.
     */
    public static List<String> getProjects() {
        String raw = props.getProperty("qase.projects", "");
        if (raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Retorna a lista de endpoints conhecidos.
     * <p>Se {@code endpoints.properties} existir, todos os endpoints definidos
     * (independente do valor true/false) são retornados. Caso contrário, é usado
     * o valor da chave {@code qase.endpoints} em {@code config.properties}.</p>
     *
     * @return lista de endpoints conhecidos.
     */
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

    /**
     * Retorna apenas os endpoints ativos (valor {@code true} em endpoints.properties).
     * Caso o arquivo não exista, retorna todos os endpoints definidos em {@code qase.endpoints}.
     *
     * @return lista de endpoints ativos.
     */
    public static List<String> getActiveEndpoints() {
        if (endpointFlags == null || endpointFlags.isEmpty()) {
            return getEndpoints();
        }

        return endpointFlags.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Retorna o token de autenticação da API definido em {@code qase.api.token}.
     *
     * @return token da API Qase ou {@code null} se não configurado.
     */
    public static String getApiToken() {
        return props.getProperty("qase.api.token");
    }

    /**
     * Retorna a URL base da API Qase.
     *
     * @return URL base (por padrão {@code https://api.qase.io/v1}).
     */
    public static String getApiBaseUrl() {
        return props.getProperty("qase.api.baseUrl", "https://api.qase.io/v1");
    }

    /**
     * Força o recarregamento das propriedades de configuração.
     * <p>Útil para testes ou cenários onde os arquivos de configuração
     * possam ter sido alterados em tempo de execução.</p>
     */
    public static void reload() {
        props.clear();
        endpointFlags = null;
        loadConfig();
    }
}
