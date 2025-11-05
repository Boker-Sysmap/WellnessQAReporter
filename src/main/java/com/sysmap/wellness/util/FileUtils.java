package com.sysmap.wellness.util;

import java.io.IOException;
import java.nio.file.*;

/**
 * Utilitário centralizado para manipulação de diretórios e caminhos de saída.
 */
public class FileUtils {

    private static final Path BASE_OUTPUT_DIR = Paths.get("output");

    /**
     * Retorna o diretório base de saída (padrão: ./output)
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
     * Retorna um subdiretório dentro de output (por exemplo: output/json, output/report)
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
     * Apaga todos os arquivos de uma pasta (sem remover a pasta).
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
