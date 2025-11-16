package com.sysmap.wellness.history;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * <h1>Gerenciador PREMIUM da Estrutura de Histórico</h1>
 *
 * <p>
 * Responsável por criar e validar toda a hierarquia de diretórios utilizada pelo
 * módulo de histórico do <b>WellnessQAReporter</b>, garantindo que a estrutura
 * necessária esteja disponível antes do processamento de KPIs, snapshots,
 * relatórios de produtividade e demais artefatos históricos.
 * </p>
 *
 * <h2>Principais responsabilidades:</h2>
 * <ul>
 *     <li>Construção da pasta base de histórico;</li>
 *     <li>Criação automática de pastas de releases, produtividade, defeitos e outras;</li>
 *     <li>Criação de estruturas específicas para cada projeto informado em <code>config.properties</code>;</li>
 *     <li>Normalização padronizada de nomes de projetos;</li>
 *     <li>Verificação de permissões de escrita;</li>
 *     <li>Logs consistentes e padronizados;</li>
 * </ul>
 *
 * <p>
 * O design deste componente segue boas práticas de:
 * </p>
 * <ul>
 *     <li>pequenos métodos (Single Responsibility);</li>
 *     <li>compatibilidade total com Java 8+;</li>
 *     <li>resiliência na criação de diretórios (uso de <code>Files.createDirectories</code>);</li>
 *     <li>mensagens de erro claras e orientadas ao usuário;</li>
 * </ul>
 *
 * <p><b>Observação:</b> A classe não depende de bibliotecas externas além do Java padrão. </p>
 */
public class HistoryDirectoryManager {

    /** Diretório raiz onde toda a estrutura será criada. */
    private static final String BASE_DIR = "historico";

    /**
     * Subdiretórios internos utilizados pelo sistema para armazenar:
     * <ul>
     *     <li>releases (KPIs e snapshots por release)</li>
     *     <li>mensal (indicadores consolidados por mês)</li>
     *     <li>produtividade (relatórios de execução por ciclo)</li>
     *     <li>estabilidade (indicadores de falhas, flakiness etc.)</li>
     *     <li>curva_execucao (tendências de execução)</li>
     *     <li>defeitos (painéis históricos de bugs)</li>
     *     <li>snapshots (dados brutos por execução)</li>
     * </ul>
     */
    private static final List<String> SUBDIRS = Arrays.asList(
        "releases",
        "mensal",
        "produtividade",
        "estabilidade",
        "curva_execucao",
        "defeitos",
        "snapshots"
    );

    /** Propriedades carregadas do config.properties. */
    private final Properties props;

    /**
     * Construtor padrão.
     *
     * @param props Propriedades do sistema contendo a chave <code>projects</code>,
     *              que lista os projetos separados por vírgula.
     */
    public HistoryDirectoryManager(Properties props) {
        this.props = props;
    }

    /**
     * <h2>Inicializa toda a estrutura de histórico necessária ao WellnessQAReporter.</h2>
     *
     * <p>A execução consiste em:</p>
     * <ol>
     *     <li>criar a pasta raiz <code>historico/</code>;</li>
     *     <li>verificar permissões de escrita;</li>
     *     <li>carregar lista de projetos do arquivo de configuração;</li>
     *     <li>criar estrutura completa (subdiretórios) para cada projeto;</li>
     *     <li>criar pasta adicional <code>historico/meta</code> para informações auxiliares;</li>
     * </ol>
     *
     * <p>
     * Caso qualquer etapa falhe, uma exceção é lançada imediatamente,
     * prevenindo estados inconsistentes no filesystem.
     * </p>
     */
    public void initializeHistoryStructure() {
        LoggerUtils.step("📚 Preparando estrutura de histórico...");

        // Cria pasta raiz
        Path base = Paths.get(BASE_DIR);
        createDirectory(base);
        validateWritePermissions(base);

        // Cria estrutura por projeto
        List<String> projects = loadProjects();
        for (String proj : projects) {
            createProjectStructure(proj);
        }

        // Meta-informações
        createDirectory(Paths.get(BASE_DIR, "meta"));

        LoggerUtils.success("📁 Estrutura de histórico criada com sucesso.");
    }

    // -------------------------------------------------------------
    //  PROJETOS
    // -------------------------------------------------------------

    /**
     * Carrega a lista de projetos a partir da propriedade
     * <code>projects</code> no arquivo de configuração.
     *
     * <p>Exemplo:</p>
     * <pre>projects=APP01, APP02, PortalWeb</pre>
     *
     * @return Lista de nomes de projetos normalizados.
     * @throws IllegalStateException se nenhum projeto estiver definido.
     */
    private List<String> loadProjects() {
        String raw = props.getProperty("projects", "").trim();

        if (raw.isEmpty()) {
            throw new IllegalStateException(
                "Nenhum projeto encontrado nas propriedades (chave: 'projects')."
            );
        }

        // Compatível com Java 8 (sem streams avançados)
        List<String> list = new ArrayList<>();
        for (String part : raw.split(",")) {
            String val = part.trim();
            if (!val.isEmpty()) list.add(val);
        }

        LoggerUtils.step("📌 Projetos detectados: " + String.join(", ", list));
        return list;
    }

    /**
     * Normaliza o nome de um projeto para garantir:
     * <ul>
     *     <li>tudo em minúsculas</li>
     *     <li>substituição de espaços por underscore</li>
     *     <li>remoção de caracteres não permitidos</li>
     * </ul>
     *
     * Exemplo:
     * <pre>"Portal Web!" → "portal_web"</pre>
     *
     * @param s Nome original do projeto.
     * @return Nome seguro para uso em diretórios.
     */
    private String normalizeProjectName(String s) {
        return s.toLowerCase()
            .replace(" ", "_")
            .replaceAll("[^a-z0-9_]", "");
    }

    // -------------------------------------------------------------
    //  CRIAÇÃO DE ESTRUTURA POR PROJETO
    // -------------------------------------------------------------

    /**
     * Cria a estrutura completa para um projeto específico.
     *
     * <p>Gera automaticamente todos os diretórios dentro de:</p>
     * <pre>historico/{subdiretorio}/{projeto-normalizado}</pre>
     *
     * @param project Nome original do projeto informado no config.
     */
    private void createProjectStructure(String project) {
        String normalized = normalizeProjectName(project);

        for (String sub : SUBDIRS) {
            Path p = Paths.get(BASE_DIR, sub, normalized);
            createDirectory(p);
        }
    }

    // -------------------------------------------------------------
    //  UTILITÁRIOS DE DIRETÓRIO
    // -------------------------------------------------------------

    /**
     * Cria um diretório de forma segura usando {@link Files#createDirectories(Path)}.
     *
     * <p>
     * A operação é idempotente: não lança erro se a pasta já existir.
     * </p>
     *
     * @param path Caminho do diretório a ser criado.
     * @throws RuntimeException caso ocorra uma falha de IO.
     */
    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
            LoggerUtils.info("📂 Diretório OK: " + path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Falha ao criar diretório: " + path, e);
        }
    }

    /**
     * Verifica se o processo possui permissão de escrita no diretório informado.
     *
     * @param path Caminho a validar.
     * @throws IllegalStateException caso a aplicação não possa escrever na pasta.
     */
    private void validateWritePermissions(Path path) {
        if (!Files.isWritable(path)) {
            throw new IllegalStateException(
                "Sem permissão de escrita no diretório: " + path.toAbsolutePath()
            );
        }
    }
}
