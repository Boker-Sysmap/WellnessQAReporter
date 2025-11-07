package com.sysmap.wellness.service;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Responsável por consolidar todos os arquivos JSON exportados dos endpoints da API Qase.
 * <p>
 * Esta classe lê os arquivos gerados previamente (armazenados em {@code output/json})
 * e constrói uma estrutura unificada de dados para todos os projetos e endpoints ativos,
 * permitindo que os relatórios possam operar sobre uma base de dados consolidada e uniforme.
 * </p>
 *
 * <p>Exemplo da estrutura retornada:</p>
 * <pre>
 * {
 *   "PROJECT_A": {
 *       "cases": [ ... ],
 *       "results": [ ... ],
 *       "defects": [ ... ]
 *   },
 *   "PROJECT_B": {
 *       "cases": [ ... ],
 *       "results": [ ... ]
 *   }
 * }
 * </pre>
 *
 * <p>Em caso de inconsistências (arquivos ausentes, vazios ou malformados),
 * a classe ignora o endpoint problemático e continua o processamento dos demais.</p>
 */
public class DataConsolidator {

    /** Diretório padrão onde os arquivos JSON gerados são armazenados. */
    private static final Path JSON_DIR = Path.of("output", "json");

    /**
     * Consolida os arquivos JSON de todos os projetos e endpoints ativos definidos
     * nos arquivos de configuração ({@code config.properties} e {@code endpoints.properties}).
     * <p>
     * O método percorre os projetos configurados, busca os arquivos correspondentes
     * a cada endpoint e realiza a leitura e normalização da estrutura JSON.
     * </p>
     *
     * @return Um {@link Map} contendo os dados consolidados no formato:
     * <pre>
     * {
     *   "PROJETO": {
     *       "endpoint1": [ ... ],
     *       "endpoint2": [ ... ]
     *   }
     * }
     * </pre>
     */
    public Map<String, JSONObject> consolidateAll() {
        Map<String, JSONObject> consolidated = new LinkedHashMap<>();

        List<String> projects = ConfigManager.getProjects();
        List<String> activeEndpoints = ConfigManager.getActiveEndpoints();

        LoggerUtils.divider();
        LoggerUtils.step("📦 Consolidando dados a partir dos arquivos JSON locais...");
        LoggerUtils.step("Projetos: " + String.join(", ", projects));
        LoggerUtils.step("Endpoints ativos: " + String.join(", ", activeEndpoints));

        // === Loop principal: para cada projeto e endpoint ===
        for (String project : projects) {
            JSONObject projectData = new JSONObject();

            for (String endpoint : activeEndpoints) {
                String fileName = String.format("%s_%s.json", project, endpoint);
                Path filePath = JSON_DIR.resolve(fileName);

                // Ignora se o arquivo não existir
                if (!Files.exists(filePath)) {
                    LoggerUtils.warn("⚠️ Arquivo não encontrado: " + filePath);
                    continue;
                }

                try {
                    String jsonContent = Files.readString(filePath).trim();

                    if (jsonContent.isBlank()) {
                        LoggerUtils.warn("⚠️ Arquivo vazio: " + filePath);
                        continue;
                    }

                    // Detecta e extrai a estrutura de dados (JSONArray ou JSONObject)
                    JSONArray entities = parseJsonEntities(jsonContent);

                    // Adiciona o endpoint consolidado ao projeto
                    projectData.put(endpoint, entities);

                    LoggerUtils.step(String.format("✅ %s: %d registros consolidados", fileName, entities.length()));
                    MetricsCollector.incrementBy("jsonRecordsLoaded", entities.length());

                } catch (IOException e) {
                    LoggerUtils.error("Erro ao ler " + fileName, e);
                } catch (Exception e) {
                    LoggerUtils.error("Erro ao processar JSON " + fileName, e);
                }
            }

            consolidated.put(project, projectData);
            LoggerUtils.success(String.format("📦 Projeto %s consolidado com %d endpoints.", project, projectData.length()));
        }

        LoggerUtils.success("🏁 Consolidação de dados concluída com sucesso!");
        return consolidated;
    }

    /**
     * Analisa o conteúdo de um arquivo JSON e tenta extrair o array principal de entidades,
     * independentemente da estrutura de origem (objeto raiz, campo "result", "entities", etc.).
     * <p>
     * A lógica é tolerante e tenta múltiplas abordagens de parsing,
     * de modo a suportar variações na estrutura retornada pela API Qase.
     * </p>
     *
     * @param jsonContent Conteúdo bruto do arquivo JSON.
     * @return Um {@link JSONArray} representando a lista de entidades extraídas.
     *         Retorna um array vazio caso não seja possível extrair dados válidos.
     */
    private JSONArray parseJsonEntities(String jsonContent) {
        try {
            // Caso mais simples: o JSON é um array puro
            if (jsonContent.startsWith("[")) {
                return new JSONArray(jsonContent);
            }

            JSONObject parsed = new JSONObject(jsonContent);

            // Estrutura comum: {"result": {"entities": [ ... ]}}
            if (parsed.has("result")) {
                Object result = parsed.get("result");

                if (result instanceof JSONObject) {
                    JSONObject resObj = (JSONObject) result;

                    if (resObj.has("entities") && resObj.get("entities") instanceof JSONArray) {
                        return resObj.getJSONArray("entities");
                    }

                    // Fallback: qualquer outro array dentro do objeto "result"
                    for (String key : resObj.keySet()) {
                        if (resObj.get(key) instanceof JSONArray) {
                            return resObj.getJSONArray(key);
                        }
                    }
                } else if (result instanceof JSONArray) {
                    return (JSONArray) result;
                }
            }

            // Fallback: primeiro array encontrado no objeto raiz
            for (String key : parsed.keySet()) {
                if (parsed.get(key) instanceof JSONArray) {
                    return parsed.getJSONArray(key);
                }
            }

            // Nenhum array encontrado — retorna vazio
            return new JSONArray();

        } catch (Exception e) {
            LoggerUtils.warn("⚠️ JSON inválido detectado (tratado como vazio)");
            return new JSONArray();
        }
    }
}
