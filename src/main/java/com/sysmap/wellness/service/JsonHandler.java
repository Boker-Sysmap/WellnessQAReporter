package com.sysmap.wellness.service;

import com.sysmap.wellness.utils.FileUtils;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe utilitária responsável por manipular arquivos JSON gerados a partir
 * dos dados obtidos da API Qase.
 *
 * <p>Esta classe atua como camada de persistência local, permitindo:</p>
 * <ul>
 *   <li>Salvar resultados de chamadas à API Qase em arquivos JSON (cache local);</li>
 *   <li>Ler arquivos JSON previamente salvos, evitando chamadas desnecessárias à API;</li>
 *   <li>Carregar automaticamente todos os endpoints configurados para um projeto.</li>
 * </ul>
 *
 * <p><b>Local padrão dos arquivos:</b> {@code /output/json/}</p>
 * <p><b>Padrão de nomenclatura:</b> {@code {projectCode}_{endpoint}.json}</p>
 *
 * <p>Exemplo:</p>
 * <pre>
 * output/json/FULLY_defect.json
 * output/json/CHUBB_case.json
 * </pre>
 */
public class JsonHandler {

    /**
     * Salva o conteúdo de um {@link JSONArray} em um arquivo JSON dentro da
     * pasta de saída {@code /output/json/}.
     *
     * <p>Se o diretório não existir, ele será criado automaticamente.</p>
     *
     * @param projectCode Código do projeto no Qase (ex: {@code FULLY}, {@code CHUBB})
     * @param endpoint Nome do endpoint da API (ex: {@code case}, {@code result}, {@code defect})
     * @param array Conteúdo em formato {@link JSONArray} a ser persistido
     */
    public void saveJsonArray(String projectCode, String endpoint, JSONArray array) {
        try {
            Path jsonDir = FileUtils.getOutputPath("json");
            if (!Files.exists(jsonDir)) {
                Files.createDirectories(jsonDir);
            }

            String fileName = String.format("%s_%s.json", projectCode, endpoint);
            Path file = jsonDir.resolve(fileName);

            // Grava o conteúdo formatado com indentação de 2 espaços
            Files.writeString(file, array.toString(2));

            LoggerUtils.success(String.format("💾 Arquivo salvo: %s (%d registros)",
                    file.getFileName(), array.length()));
            MetricsCollector.increment("filesSaved");
            MetricsCollector.incrementBy("recordsSaved", array.length());

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao salvar JSON " + projectCode + "_" + endpoint, e);
            MetricsCollector.increment("errors");
        }
    }

    /**
     * Lê um arquivo JSON previamente salvo no diretório {@code /output/json/},
     * caso ele exista. Se o arquivo não for encontrado ou estiver inválido,
     * é retornado um {@link JSONArray} vazio.
     *
     * @param projectCode Código do projeto (ex: {@code FULLY})
     * @param endpoint Nome do endpoint (ex: {@code case}, {@code result})
     * @return Um {@link JSONArray} com os dados lidos, ou vazio caso o arquivo
     *         não exista ou contenha conteúdo inválido
     */
    public JSONArray loadJsonArrayIfExists(String projectCode, String endpoint) {
        try {
            Path file = FileUtils.getOutputPath("json")
                    .resolve(String.format("%s_%s.json", projectCode, endpoint));

            // Verifica se o arquivo existe
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
     * Lê todos os arquivos JSON disponíveis para os endpoints configurados de um projeto.
     * <p>
     * Esse método é útil para reconstruir rapidamente o estado dos dados locais
     * sem a necessidade de novas consultas à API Qase.
     * </p>
     *
     * @param projectCode Código do projeto (ex: {@code FULLY})
     * @param endpoints Lista de endpoints a carregar (ex: {@code [case, result, defect]})
     * @return Um {@link Map} contendo cada endpoint associado ao seu {@link JSONArray} de dados.
     */
    public Map<String, JSONArray> loadAllEndpoints(String projectCode, List<String> endpoints) {
        Map<String, JSONArray> dataMap = new LinkedHashMap<>();
        for (String endpoint : endpoints) {
            JSONArray arr = loadJsonArrayIfExists(projectCode, endpoint);
            dataMap.put(endpoint, arr);
        }
        return dataMap;
    }
}
