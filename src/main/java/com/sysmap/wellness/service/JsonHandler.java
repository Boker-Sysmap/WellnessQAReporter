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
 * <h1>JsonHandler – Persistência Local dos Artefatos JSON do Qase</h1>
 *
 * <p>
 * Esta classe fornece uma camada centralizada de manipulação de arquivos JSON
 * gerados a partir das respostas obtidas pela API Qase. Ela atua como
 * repositório local de dados, permitindo que as próximas etapas do pipeline
 * (consolidação, análise, relatórios e KPIs) operem sem depender de novas consultas
 * externas.
 * </p>
 *
 * <h2>Responsabilidades principais:</h2>
 * <ul>
 *   <li>Persistir arrays JSON em disco, com formatação legível;</li>
 *   <li>Carregar arquivos JSON previamente salvos (cache local);</li>
 *   <li>Ler automaticamente os endpoints configurados para um projeto;</li>
 *   <li>Registrar métricas sobre quantidade de arquivos e registros manipulados;</li>
 *   <li>Tratar falhas de IO e manter resiliência contra arquivos inválidos.</li>
 * </ul>
 *
 * <h2>Local padrão de armazenamento:</h2>
 * <pre>/output/json/</pre>
 *
 * <h2>Padrão de nome de arquivo:</h2>
 * <pre>{projectCode}_{endpoint}.json</pre>
 *
 * <h2>Exemplos:</h2>
 * <pre>
 * output/json/FULLY_defect.json
 * output/json/CHUBB_case.json
 * </pre>
 */
public class JsonHandler {

    /**
     * Persiste um {@link JSONArray} em um arquivo JSON localizado em
     * <code>/output/json/</code>. O método garante que o diretório exista,
     * criando-o se necessário.
     *
     * <p>
     * O conteúdo é salvo com indentação de 2 espaços, facilitando inspeção manual
     * e auditoria dos dados.
     * </p>
     *
     * <h3>Uso típico:</h3>
     * <pre>
     * jsonHandler.saveJsonArray("FULLY", "case", casesArray);
     * </pre>
     *
     * @param projectCode código identificador do projeto no Qase
     * @param endpoint nome do endpoint da API Qase (ex: case, suite, defect)
     * @param array conteúdo JSON a ser salvo
     */
    public void saveJsonArray(String projectCode, String endpoint, JSONArray array) {
        try {
            Path jsonDir = FileUtils.getOutputPath("json");

            if (!Files.exists(jsonDir)) {
                Files.createDirectories(jsonDir);
            }

            String fileName = String.format("%s_%s.json", projectCode, endpoint);
            Path file = jsonDir.resolve(fileName);

            Files.writeString(file, array.toString(2));

            LoggerUtils.success(String.format(
                "💾 Arquivo salvo: %s (%d registros)",
                file.getFileName(),
                array.length()
            ));

            MetricsCollector.increment("filesSaved");
            MetricsCollector.incrementBy("recordsSaved", array.length());

        } catch (IOException e) {
            LoggerUtils.error("❌ Falha ao salvar JSON " + projectCode + "_" + endpoint, e);
            MetricsCollector.increment("errors");
        }
    }

    /**
     * Carrega um arquivo JSON salvo em <code>/output/json/</code>, desde que ele exista
     * e contenha um array válido.
     *
     * <p>
     * O método é resiliente: caso o arquivo esteja ausente, vazio ou corrompido,
     * retorna-se um {@link JSONArray} vazio em vez de propagar exceções.
     * </p>
     *
     * <p>
     * Essa estratégia minimiza falhas e permite que o pipeline continue mesmo com
     * inconsistências temporárias no diretório de cache.
     * </p>
     *
     * @param projectCode código do projeto
     * @param endpoint nome do endpoint desejado
     * @return conteúdo JSON carregado ou array vazio em caso de erro
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

            LoggerUtils.step(String.format(
                "📂 Arquivo carregado: %s (%d registros)",
                file.getFileName(),
                arr.length()
            ));

            MetricsCollector.incrementBy("recordsLoaded", arr.length());
            return arr;

        } catch (IOException | JSONException e) {
            LoggerUtils.error(
                "❌ Erro ao ler JSON de " + projectCode + "_" + endpoint,
                e
            );
            MetricsCollector.increment("errors");
            return new JSONArray();
        }
    }

    /**
     * Carrega em lote todos os JSONs correspondentes a uma lista de endpoints
     * para um mesmo projeto.
     *
     * <p>
     * Esse método permite reconstruir rapidamente o estado local sem necessidade
     * de chamadas à API, sendo especialmente útil em pipelines off-line ou
     * execuções de relatório sob demanda.
     * </p>
     *
     * <h3>Padrão de uso:</h3>
     * <pre>
     * List&lt;String&gt; endpoints = ConfigManager.getActiveEndpoints();
     * Map&lt;String, JSONArray&gt; data = jsonHandler.loadAllEndpoints("FULLY", endpoints);
     * </pre>
     *
     * @param projectCode código identificador do projeto
     * @param endpoints endpoints a carregar
     * @return mapa onde cada chave é o endpoint e o valor é seu conteúdo JSON
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
