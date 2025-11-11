package com.sysmap.wellness.service;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Cliente principal da API Qase.
 * Responsável por coletar dados de múltiplos endpoints (case, result, defect, etc)
 * com suporte a paginação, retry e timeout configurável.
 *
 * 🔹 Agora inclui coleta adicional de resultados (results)
 *     via hashes encontrados em defects.results.
 *
 * Essa abordagem garante que todos os resultados referenciados por defeitos
 * sejam incluídos, mesmo que não estejam vinculados diretamente a um run_id.
 */
public class QaseClient {

    private final String baseUrl;
    private final String token;

    // Endpoints globais (não exigem código de projeto)
    private static final Set<String> GLOBAL_ENDPOINTS = Set.of(
            "attachment", "author", "custom_field", "shared_parameter", "system_field", "user"
    );

    public QaseClient() {
        this.baseUrl = ConfigManager.getApiBaseUrl();
        this.token = ConfigManager.getApiToken();
    }

    /**
     * Faz a coleta de todos os endpoints ativos e configurados no arquivo de propriedades.
     *
     * @return Mapa de dados por projeto e endpoint.
     */
    public Map<String, Map<String, JSONArray>> fetchAllConfiguredData() {
        Map<String, Map<String, JSONArray>> allData = new LinkedHashMap<>();
        List<String> projects = ConfigManager.getProjects();
        List<String> endpoints = ConfigManager.getActiveEndpoints();

        for (String project : projects) {
            LoggerUtils.divider();
            LoggerUtils.success("📁 Iniciando download completo do projeto: " + project);

            Map<String, JSONArray> projectData = new LinkedHashMap<>();

            for (String endpoint : endpoints) {
                JSONArray arr = fetchEndpoint(project, endpoint);
                projectData.put(endpoint, arr);
            }

            allData.put(project, projectData);
        }

        return allData;
    }

    /**
     * Coleta os dados de um endpoint específico, com paginação e tratamento de erro.
     * @param project Código do projeto Qase
     * @param endpoint Nome do endpoint
     * @return JSONArray com todos os registros agregados
     */
    public JSONArray fetchEndpoint(String project, String endpoint) {
        JSONArray aggregate = new JSONArray();
        Set<String> seen = new HashSet<>();

        final int limit = 100;
        int offset = 0;
        int page = 1;

        try {
            // 🔹 Caso especial: coleta otimizada de "result" (por run_id + hashes)
            if (endpoint.equalsIgnoreCase("result")) {
                LoggerUtils.step("🧠 Endpoint 'result' detectado — alternando para busca por run_id e hash...");

                // 1️⃣ Busca normal via run_id
                JSONArray runs = fetchEndpoint(project, "run");
                if (runs.isEmpty()) {
                    LoggerUtils.warn("⚠️ Nenhum run encontrado para " + project + ". Pulando coleta de results.");
                } else {
                    for (int r = 0; r < runs.length(); r++) {
                        JSONObject run = runs.getJSONObject(r);
                        int runId = run.optInt("id", -1);
                        if (runId == -1) continue;

                        LoggerUtils.step(String.format("📂 Coletando resultados do run #%d (%s)", runId, project));

                        offset = 0;
                        page = 1;

                        while (true) {
                            JSONArray items = fetchResultPage(project, runId, limit, offset, page);
                            if (items.isEmpty()) break;

                            int newItems = 0;
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject obj = items.getJSONObject(i);
                                String id = extractId(obj);
                                if (id == null) id = UUID.randomUUID().toString();
                                if (seen.add(id)) {
                                    aggregate.put(obj);
                                    newItems++;
                                }
                            }

                            LoggerUtils.step(String.format("✅ Run %d — página %d: %d novos (%d totais)",
                                    runId, page, newItems, aggregate.length()));

                            if (items.length() < limit) break;
                            offset += limit;
                            page++;
                            Thread.sleep(200);
                        }
                    }
                }

                // 2️⃣ Coleta adicional de results via hashes referenciados em defects
                LoggerUtils.step("🔍 Iniciando coleta adicional de results via defect.results.hash ...");

                JSONArray defects = fetchEndpoint(project, "defect");
                int addedByHash = 0;

                for (int d = 0; d < defects.length(); d++) {
                    JSONObject defect = defects.getJSONObject(d);
                    if (!defect.has("results")) continue;

                    JSONArray resultRefs = defect.optJSONArray("results");
                    if (resultRefs == null) continue;

                    for (int i = 0; i < resultRefs.length(); i++) {
                        String hash = resultRefs.optString(i);
                        if (hash == null || hash.isEmpty()) continue;
                        if (!seen.add(hash)) continue; // já coletado

                        JSONObject resultByHash = fetchResultByHash(project, hash);
                        if (resultByHash != null) {
                            aggregate.put(resultByHash);
                            addedByHash++;
                        }
                    }
                }

                if (addedByHash > 0)
                    LoggerUtils.success(String.format("📦 %d resultados adicionais coletados via hash.", addedByHash));
                else
                    LoggerUtils.info("ℹ️ Nenhum novo result encontrado via hash.");

                MetricsCollector.incrementBy("apiResultsByHashFetched", addedByHash);
                MetricsCollector.incrementBy("apiRecordsFetched", aggregate.length());
                return aggregate;
            }

            // 🔁 Paginação para outros endpoints
            while (true) {
                boolean success = false;
                int retries = 0;
                int maxRetries = 3;
                JSONArray items = new JSONArray();

                while (!success && retries < maxRetries) {
                    try {
                        items = fetchPage(project, endpoint, limit, offset, page);
                        success = true;
                    } catch (SocketTimeoutException e) {
                        retries++;
                        long wait = (long) Math.pow(2, retries) * 1000;
                        LoggerUtils.warn(String.format(
                                "⏳ Timeout na página %d (%s@%s). Retentativa %d após %d ms...",
                                page, endpoint, project, retries, wait));
                        Thread.sleep(wait);
                    } catch (IOException e) {
                        retries++;
                        long wait = (long) Math.pow(2, retries) * 1000;
                        LoggerUtils.warn(String.format(
                                "⚠️ Erro de rede (%s@%s): %s — retry %d/%d",
                                endpoint, project, e.getMessage(), retries, maxRetries));
                        Thread.sleep(wait);
                    }
                }

                if (!success) {
                    LoggerUtils.error(String.format("❌ Falha após %d tentativas em %s@%s (página %d)",
                            maxRetries, endpoint, project, page), null);
                    break;
                }

                if (items.isEmpty()) break;

                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.getJSONObject(i);
                    String id = extractId(obj);
                    if (id == null) id = UUID.randomUUID().toString();
                    if (seen.add(id)) aggregate.put(obj);
                }

                if (items.length() < limit) break;
                offset += limit;
                page++;
                Thread.sleep(200);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LoggerUtils.error("Erro inesperado ao coletar dados de " + endpoint + "@" + project, e);
            MetricsCollector.increment("apiErrors");
        }

        LoggerUtils.success(String.format("📦 %d itens agregados para %s/%s", aggregate.length(), project, endpoint));
        MetricsCollector.incrementBy("apiRecordsFetched", aggregate.length());
        return aggregate;
    }

    /** Coleta uma página genérica de um endpoint com paginação */
    private JSONArray fetchPage(String project, String endpoint, int limit, int offset, int page)
            throws IOException {
        String urlStr = GLOBAL_ENDPOINTS.contains(endpoint)
                ? String.format("%s/%s?limit=%d&offset=%d", baseUrl, endpoint, limit, offset)
                : String.format("%s/%s/%s?limit=%d&offset=%d", baseUrl, endpoint, project, limit, offset);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Token", token);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        int status = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        if (status < 200 || status >= 300)
            throw new IOException("HTTP " + status + " ao acessar " + endpoint);

        JSONObject parsed = new JSONObject(sb.toString());
        return extractArray(parsed);
    }

    /** Coleta uma página de resultados de um run específico */
    private JSONArray fetchResultPage(String project, int runId, int limit, int offset, int page)
            throws IOException {
        String urlStr = String.format("%s/result/%s?run=%d&limit=%d&offset=%d",
                baseUrl, project, runId, limit, offset);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Token", token);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(90_000);

        int status = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        if (status < 200 || status >= 300)
            throw new IOException("HTTP " + status + " ao acessar result/" + project);

        JSONObject parsed = new JSONObject(sb.toString());
        return extractArray(parsed);
    }

    /** Busca um único result via hash (usado para relacionamentos de defeitos) */
    public JSONObject fetchResultByHash(String project, String hash) {
        String urlStr = String.format("%s/result/%s/%s", baseUrl, project, hash);
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Token", token);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);

            int status = conn.getResponseCode();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            if (status >= 200 && status < 300) {
                JSONObject parsed = new JSONObject(sb.toString());
                if (parsed.has("result") && parsed.get("result") instanceof JSONObject) {
                    return parsed.getJSONObject("result");
                }
            }

        } catch (Exception e) {
            LoggerUtils.error("Erro ao buscar result por hash " + hash, e);
        }
        return null;
    }

    /** Extrai a lista de entidades de um JSON da API */
    private JSONArray extractArray(JSONObject parsed) {
        if (parsed == null) return new JSONArray();
        if (parsed.has("result")) {
            Object res = parsed.get("result");
            if (res instanceof JSONObject) {
                JSONObject ro = (JSONObject) res;
                if (ro.has("entities") && ro.get("entities") instanceof JSONArray)
                    return ro.getJSONArray("entities");
                for (String k : ro.keySet()) {
                    if (ro.get(k) instanceof JSONArray)
                        return ro.getJSONArray(k);
                }
            } else if (res instanceof JSONArray) {
                return (JSONArray) res;
            }
        }
        return new JSONArray();
    }

    /** Extrai um ID genérico de um objeto JSON */
    private String extractId(JSONObject o) {
        String[] keys = {"id", "case_id", "result_id", "run_id", "defect_id", "hash"};
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) return String.valueOf(o.get(k));
        }
        return null;
    }
}
