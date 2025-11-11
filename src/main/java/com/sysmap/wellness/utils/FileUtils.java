package com.sysmap.wellness.utils;

import java.io.IOException;
import java.nio.file.*;

/**
 * Classe utilitária responsável pela manipulação de diretórios e caminhos de saída.
 *
 * <p>Centraliza todas as operações relacionadas à criação, obtenção e limpeza de diretórios
 * utilizados pelo sistema, garantindo que a estrutura de pastas necessária para geração
 * de relatórios e arquivos JSON esteja sempre disponível.</p>
 *
 * <p>Por padrão, todas as operações são realizadas dentro do diretório base
 * <code>./output</code>.</p>
 *
 * <p>Esta classe é utilizada por diversos serviços (como {@code JsonHandler}, {@code ReportGenerator}, etc.)
 * para garantir consistência na estrutura de arquivos de saída.</p>
 *
 * <h3>Exemplo de uso:</h3>
 * <pre>{@code
 * Path jsonDir = FileUtils.getOutputPath("json");
 * Path reportsDir = FileUtils.getOutputPath("reports");
 * FileUtils.clearDirectory(reportsDir);
 * }</pre>
 *
 * @author
 * @version 1.0
 */
public class FileUtils {

    /** Caminho base padrão onde os arquivos de saída são armazenados (./output). */
    private static final Path BASE_OUTPUT_DIR = Paths.get("output");

    /**
     * Obtém o diretório base de saída do sistema.
     *
     * <p>Se o diretório não existir, ele é criado automaticamente.</p>
     *
     * @return {@link Path} representando o diretório base de saída (padrão: <code>./output</code>)
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
     * Obtém (ou cria, se necessário) um subdiretório dentro do diretório de saída.
     *
     * <p>Por exemplo, para o parâmetro <code>"json"</code>, o caminho final será:
     * <code>./output/json</code>.</p>
     *
     * @param subFolder Nome da subpasta dentro do diretório de saída (ex: "json", "reports")
     * @return {@link Path} representando o caminho completo do subdiretório criado ou existente
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
     * Remove todos os arquivos contidos em um diretório específico,
     * sem excluir o diretório em si.
     *
     * <p>Este método é útil para limpar diretórios temporários ou pastas de saída
     * antes de uma nova execução.</p>
     *
     * @param dir Caminho do diretório que deve ser limpo
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
