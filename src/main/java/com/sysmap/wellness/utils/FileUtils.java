package com.sysmap.wellness.utils;

import java.io.IOException;
import java.nio.file.*;

/**
 * Utilitário centralizado para manipulação de diretórios utilizados pelo
 * WellnessQAReporter.
 *
 * <p>Esta classe garante que todos os caminhos necessários para geração de
 * relatórios, exportação de JSONs, snapshots e artefatos de execução sejam
 * criados e gerenciados de forma segura e padronizada.</p>
 *
 * <p>Por padrão, todo o conteúdo gerado pelo sistema é armazenado sob o diretório
 * <strong>{@code ./output}</strong>, que funciona como raiz para:</p>
 *
 * <ul>
 *     <li>{@code /output/json} – cache local dos endpoints Qase</li>
 *     <li>{@code /output/reports} – relatórios Excel consolidados</li>
 *     <li>{@code /output/snapshots} – dumps intermediários</li>
 *     <li>outros subdiretórios necessários</li>
 * </ul>
 *
 * <p>Esta classe é utilizada diretamente por serviços como:
 * {@code JsonHandler}, {@code ReportGenerator}, {@code DataConsolidator},
 * garantindo padronização e isolamento das operações de I/O.</p>
 *
 * <h3>Exemplo típico de uso:</h3>
 * <pre>{@code
 * Path jsonDir = FileUtils.getOutputPath("json");
 * Path reportsDir = FileUtils.getOutputPath("reports");
 *
 * // Limpa a pasta antes de gerar novos arquivos
 * FileUtils.clearDirectory(reportsDir);
 * }</pre>
 *
 * @author
 * @version 1.0
 */
public class FileUtils {

    /** Diretório padrão de saída onde todo o conteúdo gerado será armazenado. */
    private static final Path BASE_OUTPUT_DIR = Paths.get("output");

    /**
     * Retorna o diretório base de saída do sistema ({@code ./output}), criando-o
     * se ainda não existir.
     *
     * <p>O método garante que:</p>
     * <ul>
     *     <li>o diretório existe no sistema de arquivos;</li>
     *     <li>possui permissões adequadas para escrita;</li>
     *     <li>erros de I/O são registrados no log padronizado.</li>
     * </ul>
     *
     * @return Instância de {@link Path} apontando para {@code ./output}.
     */
    public static Path getBaseOutputPath() {
        try {
            if (!Files.exists(BASE_OUTPUT_DIR)) {
                Files.createDirectories(BASE_OUTPUT_DIR);
                LoggerUtils.step("Criado diretório base: " + BASE_OUTPUT_DIR.toAbsolutePath());
            }
        } catch (IOException e) {
            LoggerUtils.error("Erro ao criar diretório base: " + BASE_OUTPUT_DIR, e);
        }
        return BASE_OUTPUT_DIR;
    }

    /**
     * Obtém o caminho de um subdiretório dentro de {@code ./output}, criando-o
     * automaticamente caso ainda não exista.
     *
     * <p>Este método padroniza toda a estrutura de saída do sistema,
     * garantindo previsibilidade nas operações de escrita de arquivos.</p>
     *
     * <p>Exemplos de chamadas:</p>
     * <pre>{@code
     * FileUtils.getOutputPath("json");     // → ./output/json
     * FileUtils.getOutputPath("reports");  // → ./output/reports
     * }</pre>
     *
     * @param subFolder Nome da subpasta a ser criada/retornada.
     * @return {@link Path} para o subdiretório solicitado.
     */
    public static Path getOutputPath(String subFolder) {
        Path dir = BASE_OUTPUT_DIR.resolve(subFolder);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                LoggerUtils.step("Criado diretório: " + dir.toAbsolutePath());
            }
        } catch (IOException e) {
            LoggerUtils.error("Erro ao criar subdiretório: " + dir, e);
        }
        return dir;
    }

    /**
     * Remove recursivamente todos os arquivos contidos dentro do diretório informado,
     * mantendo a estrutura da pasta.
     *
     * <p>Este método <strong>não remove subpastas</strong>; apenas limpa arquivos.
     * É especialmente útil para:</p>
     *
     * <ul>
     *     <li>limpar pasta de relatórios antes de gerar um novo lote;</li>
     *     <li>remover arquivos temporários de execução;</li>
     *     <li>garantir que pastas de saída estejam vazias para CI/CD;</li>
     * </ul>
     *
     * <p>Erros de deleção são registrados sem interromper o fluxo do sistema.</p>
     *
     * @param dir Diretório cujos arquivos devem ser apagados.
     */
    public static void clearDirectory(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
            LoggerUtils.info("Limpo diretório: " + dir.getFileName());
        } catch (IOException e) {
            LoggerUtils.error("Erro ao limpar diretório " + dir, e);
        }
    }
}
