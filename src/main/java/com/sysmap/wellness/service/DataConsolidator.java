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
 * Consolida todos os arquivos JSON exportados dos endpoints da API Qase
 * em uma estrutura unificada e genérica para uso por qualquer relatório.
 */
public class DataConsolidator {

    private static final Path JSON_DIR = Path.of("output", "json");

    /**
     * Consolida os arquivos JSON de todos os projetos e endpoints ativos.
     *
     * @return Mapa consolidado no formato:
     *         { "PROJETO": { "endpoint1": [...], "endpoint2": [...] } }
     */
    public Map<String, JSONObject> consolidateAll() {
        Map<String, JSONObject> consolidated = new LinkedHashMap<>();

        List<String> projects = ConfigManager.getProjects();
        List<String> activeEndpoints = ConfigManager.getActiveEndpoints();

        LoggerUtils.divider();
        LoggerUtils.step("📦 Consolidando dados a partir dos arquivos JSON locais...");
        LoggerUtils.step("Projetos: " + String.join(", ", projects));
        LoggerUtils.step("Endpoints ativos: " + String.join(", ", activeEndpoints));

        for (String project : projects) {
            JSONObject projectData = new JSONObject();

            for (String endpoint : activeEndpoints) {
                String fileName = String.format("%s_%s.json", project, endpoint);
                Path filePath = JSON_DIR.resolve(fileName);

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

                    // Detecta estrutura base — pode ser um array puro ou um objeto
                    JSONArray entities = parseJsonEntities(jsonContent);

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
     * Tenta extrair o array de entidades de um JSON, independentemente do formato.
     */
    private JSONArray parseJsonEntities(String jsonContent) {
        try {
            if (jsonContent.startsWith("[")) {
                return new JSONArray(jsonContent);
            }

            JSONObject parsed = new JSONObject(jsonContent);

            if (parsed.has("result")) {
                Object result = parsed.get("result");
                if (result instanceof JSONObject) {
                    JSONObject resObj = (JSONObject) result;
                    if (resObj.has("entities") && resObj.get("entities") instanceof JSONArray) {
                        return resObj.getJSONArray("entities");
                    }
                    // fallback — qualquer array interno
                    for (String key : resObj.keySet()) {
                        if (resObj.get(key) instanceof JSONArray) {
                            return resObj.getJSONArray(key);
                        }
                    }
                } else if (result instanceof JSONArray) {
                    return (JSONArray) result;
                }
            }

            // fallback total — primeiro array encontrado no objeto raiz
            for (String key : parsed.keySet()) {
                if (parsed.get(key) instanceof JSONArray) {
                    return parsed.getJSONArray(key);
                }
            }

            // nada encontrado
            return new JSONArray();

        } catch (Exception e) {
            LoggerUtils.warn("⚠️ JSON inválido detectado (tratado como vazio)");
            return new JSONArray();
        }
    }
}
