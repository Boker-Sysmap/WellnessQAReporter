package com.sysmap.wellness.service;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.service.consolidator.DataConsolidator;
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
 * <h1>QaseClient – Cliente principal da API Qase</h1>
 *
 * <p>
 * Esta classe encapsula toda a lógica de comunicação com a API do Qase,
 * incluindo:
 * </p>
 *
 * <ul>
 *   <li>Coleta de dados de múltiplos endpoints (case, result, defect, run, etc.);</li>
 *   <li>Suporte a paginação (limit/offset) com agregação de todas as páginas;</li>
 *   <li>Mecanismo de retry com backoff exponencial em caso de erros de rede ou timeout;</li>
 *   <li>Tratamento diferenciado para o endpoint <b>result</b>, incluindo:
 *     <ul>
 *       <li>coleta por <code>run_id</code> (padrão Qase);</li>
 *       <li>coleta adicional de resultados por <b>hash</b> referenciado em <code>defects.results</code>.</li>
 *     </ul>
 *   </li>
 *   <li>Deduplicação de registros via identificação genérica (id/hash/case_id/etc.);</li>
 *   <li>Coleta tanto de endpoints globais quanto específicos de projeto.</li>
 * </ul>
 *
 * <p>
 * A estratégia de coleta foi desenhada para garantir que todos os resultados
 * referenciados por defeitos sejam trazidos, mesmo quando não há vínculo direto
 * por <code>run_id</code>, diminuindo lacunas na correlação defeito → execução → caso.
 * </p>
 *
 * <h2>Funcionamento em alto nível:</h2>
 * <ol>
 *   <li>Os projetos e endpoints são obtidos a partir do {@link ConfigManager};</li>
 *   <li>Para cada projeto, percorre-se a lista de endpoints ativos;</li>
 *   <li>Para cada endpoint:
 *     <ul>
 *       <li>Se for <code>result</code>, utiliza a estratégia de <b>run_id + hash</b>;</li>
 *       <li>Demais endpoints usam paginação padrão (limit/offset);</li>
 *     </ul>
 *   </li>
 *   <li>As respostas são agregadas em um {@link JSONArray} e retornadas ao chamador.</li>
 * </ol>
 *
 * <p>
 * Os métodos públicos desta classe são os pontos de entrada principais para o
 * pipeline de coleta de dados que será posteriormente consumido pelo
 * {@link com.sysmap.wellness.service.JsonHandler} e pelo
 * {@link DataConsolidator}.
 * </p>
 */
public class QaseClient {

    /** URL base da API Qase, carregada a partir da configuração. */
    private final String baseUrl;

    /** Token de autenticação utilizado em todas as requisições HTTP. */
    private final String token;

    /**
     * Conjunto de endpoints globais, isto é, que não exigem código de projeto
     * na URL (ex.: /user, /attachment, etc.).
     *
     * <p>
     * Para esses endpoints, a URL é montada sem o parâmetro de projeto.
     * </p>
     */
    private static final Set<String> GLOBAL_ENDPOINTS = Set.of(
        "attachment", "author", "custom_field", "shared_parameter", "system_field", "user"
    );

    /**
     * Cria uma nova instância do {@code QaseClient} utilizando as configurações
     * de baseUrl e token fornecidas pelo {@link ConfigManager}.
     *
     * <p>
     * Este construtor não executa nenhuma chamada de rede; apenas prepara
     * o cliente para uso.
     * </p>
     */
    public QaseClient() {
        this.baseUrl = ConfigManager.getApiBaseUrl();
        this.token = ConfigManager.getApiToken();
    }

    /**
     * Faz a coleta de todos os endpoints ativos, conforme configurados em
     * {@link ConfigManager#getActiveEndpoints()}, para todos os projetos
     * listados em {@link ConfigManager#getProjects()}.
     *
     * <p>
     * O retorno é um mapa em dois níveis:
     * </p>
     *
     * <pre>
     * {
     *   "PROJECT1": {
     *       "case":   [...],
     *       "suite":  [...],
     *       "defect": [...],
     *       ...
     *   },
     *   "PROJECT2": {
     *       ...
     *   }
     * }
     * </pre>
     *
     * <p>
     * Cada endpoint é coletado através de {@link #fetchEndpoint(String, String)},
     * incluindo o tratamento especial do endpoint <code>result</code>.
     * </p>
     *
     * @return mapa contendo os dados por projeto e por endpoint.
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
     * Coleta os dados de um endpoint específico para um projeto, com suporte
     * a paginação, retry e lógica diferenciada para o endpoint <code>result</code>.
     *
     * <p><b>Comportamento especial para "result":</b></p>
     * <ul>
     *   <li>Primeiro, coleta todos os resultados ligados a cada <code>run_id</code> do projeto;</li>
     *   <li>Depois, coleta resultados adicionais usando <b>hashes</b> encontrados em
     *       <code>defect.results</code>, garantindo completude da base.</li>
     * </ul>
     *
     * <p><b>Para os demais endpoints:</b> é utilizada paginação clássica via
     * <code>limit</code> e <code>offset</code>, com múltiplas tentativas e
     * backoff exponencial em caso de falha.</p>
     *
     * @param project  código do projeto Qase
     * @param endpoint nome do endpoint (ex.: case, result, defect, run)
     * @return um {@link JSONArray} contendo todos os registros agregados,
     *         sem duplicidade (deduplicados por ID genérico)
     */
    public JSONArray fetchEndpoint(String project, String endpoint) {
        JSONArray aggregate = new JSONArray();
        Set<String> seen = new HashSet<>();

        final int limit = 100;
        int offset = 0;
        int page = 1;

        try {
            // 🔹 Tratamento específico para o endpoint "result"
            if (endpoint.equalsIgnoreCase("result")) {
                LoggerUtils.step("🧠 Endpoint 'result' detectado — alternando para busca por run_id e hash...");

                // 1️⃣ Coleta normal de resultados por run_id
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

                            LoggerUtils.step(String.format(
                                "✅ Run %d — página %d: %d novos (%d totais)",
                                runId, page, newItems, aggregate.length()
                            ));

                            if (items.length() < limit) break;
                            offset += limit;
                            page++;
                            Thread.sleep(200);
                        }
                    }
                }

                // 2️⃣ Coleta de resultados adicionais via hash em defects.results
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

                if (addedByHash > 0) {
                    LoggerUtils.success(String.format(
                        "📦 %d resultados adicionais coletados via hash.",
                        addedByHash
                    ));
                } else {
                    LoggerUtils.info("ℹ️ Nenhum novo result encontrado via hash.");
                }

                MetricsCollector.incrementBy("apiResultsByHashFetched", addedByHash);
                MetricsCollector.incrementBy("apiRecordsFetched", aggregate.length());
                return aggregate;
            }

            // 🔁 Paginação padrão para outros endpoints
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
                    LoggerUtils.error(String.format(
                        "❌ Falha após %d tentativas em %s@%s (página %d)",
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

        LoggerUtils.success(String.format(
            "📦 %d itens agregados para %s/%s",
            aggregate.length(), project, endpoint
        ));
        MetricsCollector.incrementBy("apiRecordsFetched", aggregate.length());
        return aggregate;
    }

    /**
     * Coleta uma página de dados genérica de um endpoint, aplicando limit/offset,
     * montando a URL adequada para endpoints globais ou dependentes de projeto.
     *
     * <p>
     * O método não faz retry nem agregação — ele apenas executa uma chamada
     * HTTP simples e converte a resposta em {@link JSONArray}, utilizando
     * {@link #extractArray(JSONObject)} para extrair o conjunto de entidades.
     * </p>
     *
     * @param project código do projeto (ignorando para endpoints globais)
     * @param endpoint nome do endpoint da API
     * @param limit tamanho da página (parâmetro <code>limit</code>)
     * @param offset deslocamento (parâmetro <code>offset</code>)
     * @param page número lógico da página (usado apenas para logs)
     * @return array de entidades retornadas pela API
     * @throws IOException em caso de erro HTTP ou falha de rede
     */
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

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " ao acessar " + endpoint);
        }

        JSONObject parsed = new JSONObject(sb.toString());
        return extractArray(parsed);
    }

    /**
     * Coleta uma página de resultados (endpoint <code>result</code>) para um
     * run específico, utilizando o parâmetro <code>run</code> na chamada.
     *
     * <p>
     * Assim como {@link #fetchPage(String, String, int, int, int)}, este método
     * não faz agregação nem retry; isso é feito em {@link #fetchEndpoint(String, String)}.
     * </p>
     *
     * @param project código do projeto
     * @param runId   identificador do run
     * @param limit   tamanho da página
     * @param offset  deslocamento
     * @param page    número lógico da página (apenas para logs)
     * @return array de resultados retornados pela API
     * @throws IOException em caso de erro HTTP ou falha de rede
     */
    private JSONArray fetchResultPage(String project, int runId, int limit, int offset, int page)
        throws IOException {

        String urlStr = String.format(
            "%s/result/%s?run=%d&limit=%d&offset=%d",
            baseUrl, project, runId, limit, offset
        );

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

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " ao acessar result/" + project);
        }

        JSONObject parsed = new JSONObject(sb.toString());
        return extractArray(parsed);
    }

    /**
     * Busca um único resultado (result) da API do Qase utilizando o seu
     * <b>hash</b>, normalmente referenciado em {@code defect.results}.
     *
     * <p>
     * Esta chamada é utilizada como complemento à coleta por run_id, garantindo
     * que resultados não associados diretamente a um run também sejam obtidos.
     * </p>
     *
     * @param project código do projeto
     * @param hash    identificador hash do result
     * @return objeto JSON do result, ou {@code null} em caso de erro ou resposta inválida
     */
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

    /**
     * Extrai, de forma tolerante, a lista de entidades de um JSON retornado
     * pela API Qase.
     *
     * <p>São tratados os seguintes formatos:</p>
     * <ul>
     *   <li><code>{"result":{"entities":[...]}}</code></li>
     *   <li><code>{"result":[...]}</code></li>
     *   <li>Primeiro {@link JSONArray} encontrado em qualquer chave de <code>result</code>;</li>
     *   <li>Caso contrário, retorna array vazio.</li>
     * </ul>
     *
     * @param parsed JSON já parseado da resposta HTTP.
     * @return array de entidades encontrado, ou um {@link JSONArray} vazio se não houver.
     */
    private JSONArray extractArray(JSONObject parsed) {
        if (parsed == null) return new JSONArray();

        if (parsed.has("result")) {
            Object res = parsed.get("result");

            if (res instanceof JSONObject) {
                JSONObject ro = (JSONObject) res;

                if (ro.has("entities") && ro.get("entities") instanceof JSONArray) {
                    return ro.getJSONArray("entities");
                }

                for (String k : ro.keySet()) {
                    if (ro.get(k) instanceof JSONArray) {
                        return ro.getJSONArray(k);
                    }
                }
            } else if (res instanceof JSONArray) {
                return (JSONArray) res;
            }
        }

        return new JSONArray();
    }

    /**
     * Extrai um identificador lógico de um objeto JSON genérico,
     * tentando diferentes chaves comuns em entidades do Qase.
     *
     * <p>Ordem de busca:</p>
     * <ol>
     *   <li>id</li>
     *   <li>case_id</li>
     *   <li>result_id</li>
     *   <li>run_id</li>
     *   <li>defect_id</li>
     *   <li>hash</li>
     * </ol>
     *
     * <p>
     * O ID retornado é usado para deduplicar itens ao agregar múltiplas páginas
     * ou múltiplas fontes (run_id + hash).
     * </p>
     *
     * @param o objeto JSON do qual se deseja extrair uma chave identificadora.
     * @return valor de ID em forma de {@link String}, ou {@code null} se nenhuma chave existir.
     */
    private String extractId(JSONObject o) {
        String[] keys = {"id", "case_id", "result_id", "run_id", "defect_id", "hash"};
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                return String.valueOf(o.get(k));
            }
        }
        return null;
    }
}
