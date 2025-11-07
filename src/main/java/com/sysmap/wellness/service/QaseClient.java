package com.sysmap.wellness.service;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
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
import java.util.concurrent.TimeUnit;

/**
 * Cliente HTTP especializado para comunicação com a <b>API Qase</b>.
 *
 * <p>Esta classe implementa lógica de paginação, controle de timeout,
 * retentativas automáticas (retry) com backoff exponencial e tratamento de endpoints
 * otimizados (como o endpoint {@code result}, que é consultado por {@code run_id}).</p>
 *
 * <p>Ela é responsável por recuperar e agregar os dados brutos dos endpoints configurados
 * no arquivo {@code endpoints.properties} e posteriormente utilizados nos relatórios Excel.</p>
 *
 * <p><b>Recursos principais:</b></p>
 * <ul>
 *   <li>Autenticação via token (definido em {@code config.properties});</li>
 *   <li>Suporte a paginação automática com parâmetros {@code limit} e {@code offset};</li>
 *   <li>Retentativas automáticas em caso de falha de rede ou timeout (com backoff exponencial);</li>
 *   <li>Controle de duplicidade de registros baseado em chaves identificadoras (id, case_id, etc);</li>
 *   <li>Medição de métricas de performance e logging detalhado de cada requisição.</li>
 * </ul>
 */
public class QaseClient {

    /** URL base da API Qase (ex: https://api.qase.io/v1) */
    private final String baseUrl;

    /** Token de autenticação configurado em {@code config.properties} */
    private final String token;

    /** Endpoints globais que não exigem código de projeto na URL */
    private static final Set<String> GLOBAL_ENDPOINTS = Set.of(
            "attachment", "author", "custom_field", "shared_parameter", "system_field", "user"
    );

    /**
     * Construtor padrão — inicializa com base nas configurações de {@link ConfigManager}.
     */
    public QaseClient() {
        this.baseUrl = ConfigManager.getApiBaseUrl();
        this.token = ConfigManager.getApiToken();
    }

    /**
     * Executa a coleta completa de dados de todos os projetos e endpoints configurados.
     *
     * @return Mapa contendo os dados agregados por projeto e endpoint.
     *         Estrutura: {@code { "PROJETO": { "endpoint1": [...], "endpoint2": [...] } }}
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
     * Busca os dados de um endpoint específico, aplicando paginação automática
     * e controle de retentativas em caso de erro.
     *
     * <p>Se o endpoint for {@code result}, a busca é realizada por {@code run_id}
     * (modo otimizado de coleta de resultados).</p>
     *
     * @param project Código do projeto (ex: FULLY, CHUBB)
     * @param endpoint Nome do endpoint (ex: case, result, defect)
     * @return Um {@link JSONArray} contendo os registros agregados do endpoint.
     */
    public JSONArray fetchEndpoint(String project, String endpoint) {
        JSONArray aggregate = new JSONArray();
        Set<String> seen = new HashSet<>();

        final int limit = 100;
        int offset = 0;
        int page = 1;

        try {
            // 🔹 Modo otimizado — busca resultados por run_id
            if (endpoint.equalsIgnoreCase("result")) {
                LoggerUtils.step("🧠 Endpoint 'result' detectado — alternando para busca por run_id...");

                JSONArray runs = fetchEndpoint(project, "run");
                if (runs.isEmpty()) {
                    LoggerUtils.warn("⚠️ Nenhum run encontrado para " + project + ". Pulando coleta de results.");
                    return aggregate;
                }

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

                LoggerUtils.success(String.format("📦 %d resultados agregados (modo otimizado por run_id)", aggregate.length()));
                MetricsCollector.incrementBy("apiRecordsFetched", aggregate.length());
                return aggregate;
            }

            // 🔁 Paginação padrão para outros endpoints
            while (true) {
                boolean success = false;
                int retries = 0;
                int maxRetries = endpoint.equalsIgnoreCase("result") ? 5 : 3;
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

                if (items.isEmpty()) {
                    LoggerUtils.info("Nenhum item encontrado, encerrando paginação.");
                    break;
                }

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

                LoggerUtils.step(String.format("✅ Página %d: %d novos (%d totais)", page, newItems, aggregate.length()));

                if (items.length() < limit) break;

                offset += limit;
                page++;
                Thread.sleep(250);
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

    /**
     * Executa uma chamada HTTP paginada comum para um endpoint.
     *
     * @param project Código do projeto
     * @param endpoint Nome do endpoint
     * @param limit Quantidade máxima de registros por página
     * @param offset Deslocamento (offset) para paginação
     * @param page Número da página (apenas para logs)
     * @return {@link JSONArray} com os dados da página
     * @throws IOException Erro de comunicação HTTP
     * @throws SocketTimeoutException Timeout de leitura ou conexão
     */
    private JSONArray fetchPage(String project, String endpoint, int limit, int offset, int page)
            throws IOException, SocketTimeoutException {

        String urlStr = GLOBAL_ENDPOINTS.contains(endpoint)
                ? String.format("%s/%s?limit=%d&offset=%d", baseUrl, endpoint, limit, offset)
                : String.format("%s/%s/%s?limit=%d&offset=%d", baseUrl, endpoint, project, limit, offset);

        LoggerUtils.step(String.format("🔗 [Página %d] GET %s", page, urlStr));

        long start = System.nanoTime();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Token", token);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(endpoint.equalsIgnoreCase("result") ? 300_000 : 60_000);
        conn.connect();

        int status = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        LoggerUtils.step(String.format("⏱ Página %d concluída em %d ms", page, elapsed));

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " ao acessar " + endpoint);
        }

        return extractArray(new JSONObject(sb.toString()));
    }

    /**
     * Busca uma página de resultados (result) filtrada por {@code run_id}.
     *
     * @param project Código do projeto
     * @param runId ID do run
     * @param limit Limite de registros por página
     * @param offset Offset para paginação
     * @param page Número da página (para logs)
     * @return {@link JSONArray} contendo os resultados
     * @throws IOException Em caso de erro HTTP
     */
    private JSONArray fetchResultPage(String project, int runId, int limit, int offset, int page)
            throws IOException {

        String urlStr = String.format("%s/result/%s?run=%d&limit=%d&offset=%d",
                baseUrl, project, runId, limit, offset);
        LoggerUtils.step(String.format("🔗 [Run %d - Página %d] GET %s", runId, page, urlStr));

        long start = System.nanoTime();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Token", token);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(90_000);
        conn.connect();

        int status = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        LoggerUtils.step(String.format("⏱ Run %d - página %d concluída em %d ms", runId, page, elapsed));

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " ao acessar result/" + project);
        }

        return extractArray(new JSONObject(sb.toString()));
    }

    /**
     * Busca um registro específico do endpoint {@code result} pelo hash único.
     *
     * @param project Código do projeto
     * @param hash Identificador único do resultado
     * @return {@link JSONObject} com os dados do resultado, ou {@code null} se não encontrado.
     */
    public JSONObject fetchResultByHash(String project, String hash) {
        String urlStr = String.format("%s/result/%s/%s", baseUrl, project, hash);
        LoggerUtils.step("🔍 Buscando result por hash em: " + urlStr);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Token", token);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            conn.connect();

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
            } else {
                LoggerUtils.warn("⚠️ HTTP " + status + " ao buscar result hash " + hash);
            }

        } catch (Exception e) {
            LoggerUtils.error("Erro ao buscar result por hash " + hash, e);
        }
        return null;
    }

    /** Extrai o array de dados principal de um JSON retornado pela API. */
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

    /** Retorna um identificador único para o objeto, com base em campos conhecidos. */
    private String extractId(JSONObject o) {
        String[] keys = {"id", "case_id", "result_id", "run_id", "defect_id"};
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) return String.valueOf(o.get(k));
        }
        return null;
    }
}
