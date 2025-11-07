package com.sysmap.wellness.service;

import com.sysmap.wellness.util.FileUtils;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Responsável por salvar e carregar dados JSON da API Qase.
 * Agora suporta múltiplos endpoints e segue o modelo modular.
 *
 * - Salva arquivos em: /output/json/
 * - Nome dos arquivos: {projectCode}_{endpoint}.json
 * - Lê arquivos se já existirem (útil para debug e cache local)
 */
public class JsonHandler {

    /**
     * Salva um JSONArray em um arquivo JSON dentro da pasta de saída.
     *
     * @param projectCode Código do projeto (ex: FULLY)
     * @param endpoint Nome do endpoint (ex: case, result, defect, milestone)
     * @param array Conteúdo a ser salvo
     */
    public void saveJsonArray(String projectCode, String endpoint, JSONArray array) {
        try {
            Path jsonDir = FileUtils.getOutputPath("json");
            if (!Files.exists(jsonDir)) Files.createDirectories(jsonDir);

            String fileName = String.format("%s_%s.json", projectCode, endpoint);
            Path file = jsonDir.resolve(fileName);

            Files.writeString(file, array.toString(2));

            LoggerUtils.success(String.format("💾 Arquivo salvo: %s (%d registros)", file.getFileName(), array.length()));
            MetricsCollector.increment("filesSaved");
            MetricsCollector.incrementBy("recordsSaved", array.length());

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao salvar JSON " + projectCode + "_" + endpoint, e);
            MetricsCollector.increment("errors");
        }
    }

    /**
     * Lê um arquivo JSON existente, se houver.
     * Retorna JSONArray vazio caso o arquivo não exista ou esteja inválido.
     *
     * @param projectCode Código do projeto
     * @param endpoint Nome do endpoint
     * @return JSONArray carregado do arquivo, ou vazio
     */
    public JSONArray loadJsonArrayIfExists(String projectCode, String endpoint) {
        try {
            Path file = FileUtils.getOutputPath("json")
                    .resolve(String.format("%s_%s.json", projectCode, endpoint));

            if (!Files.exists(file)) {
                LoggerUtils.warn("⚠️ Arquivo JSON não encontrado: " + file.getFileName());
                return new JSONArray();
            }

            String content = Files.readString(file);
            if (content == null || content.isBlank()) {
                LoggerUtils.warn("⚠️ Arquivo JSON vazio: " + file.getFileName());
                return new JSONArray();
            }

            JSONArray arr = new JSONArray(content);
            LoggerUtils.step(String.format("📂 Arquivo carregado: %s (%d registros)",
                    file.getFileName(), arr.length()));
            MetricsCollector.incrementBy("recordsLoaded", arr.length());

            return arr;

        } catch (IOException | JSONException e) {
            LoggerUtils.error("❌ Erro ao ler JSON de " + projectCode + "_" + endpoint, e);
            MetricsCollector.increment("errors");
            return new JSONArray();
        }
    }

    /**
     * Lê todos os arquivos de endpoints disponíveis para um projeto.
     * (ex: case, result, defect, milestone)
     *
     * @param projectCode código do projeto
     * @param endpoints lista de endpoints configurados
     * @return mapa contendo endpoint - JSONArray
     */
    public java.util.Map<String, JSONArray> loadAllEndpoints(String projectCode, java.util.List<String> endpoints) {
        java.util.Map<String, JSONArray> dataMap = new java.util.LinkedHashMap<>();
        for (String endpoint : endpoints) {
            JSONArray arr = loadJsonArrayIfExists(projectCode, endpoint);
            dataMap.put(endpoint, arr);
        }
        return dataMap;
    }
}
