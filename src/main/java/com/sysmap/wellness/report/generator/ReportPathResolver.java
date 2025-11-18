package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Responsável por preparar o caminho final do relatório
 * e extrair o identificador de release a partir do nome
 * do arquivo (fallback).
 */
public class ReportPathResolver {

    /**
     * Garante a criação do diretório de saída e retorna o caminho final
     * onde o relatório será gravado.
     *
     * @param outputPath Caminho indicado pelo usuário.
     * @return Caminho final ajustado dentro de /output/reports.
     * @throws IOException Se não for possível criar diretórios.
     */
    public Path prepareOutputPath(Path outputPath) throws IOException {

        Path dir = Path.of("output", "reports");
        if (!Files.exists(dir)) Files.createDirectories(dir);

        Path finalPath = dir.resolve(outputPath.getFileName());

        LoggerUtils.step("📄 Arquivo final: " + finalPath);
        return finalPath;
    }

    /**
     * Extrai o identificador de release a partir do nome do arquivo
     * (sem extensão). Exemplo:
     *
     * <pre>
     *   fully_3.2.0_PROD.xlsx → fully_3.2.0_PROD
     * </pre>
     *
     * @param finalPath Caminho final do arquivo.
     * @return Nome do arquivo sem extensão.
     */
    public String extractReleaseIdFromFilename(Path finalPath) {
        String name = finalPath.getFileName().toString();
        int idx = name.lastIndexOf(".");
        return idx == -1 ? name : name.substring(0, idx);
    }
}
