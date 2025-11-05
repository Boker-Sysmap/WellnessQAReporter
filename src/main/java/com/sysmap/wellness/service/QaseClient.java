package com.sysmap.wellness.api;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.service.JsonHandler;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class QaseClient {

    private static final String BASE = ConfigManager.getBaseUrl();
    private static final String TOKEN = ConfigManager.getToken();

    private final JsonHandler jsonHandler = new JsonHandler();

    /** Baixa todos os endpoints configurados para todos os projetos. */
    public void downloadAllProjectData() {
        for (String project : ConfigManager.getProjects()) {
            LoggerUtils.divider();
            LoggerUtils.success("📁 Iniciando download completo do projeto: " + project);

            for (String endpoint : ConfigManager.getEndpoints()) {
                LoggerUtils.step("🔹 Endpoint: " + endpoint);
                JSONArray aggregated = fetchAll(project, endpoint);
                if (aggregated != null && aggregated.length() > 0) {
                    jsonHandler.saveJsonArray(project, endpoint, aggregated);
                    LoggerUtils.metric(project + "_" + endpoint + "_total", aggregated.length());
                } else {
                    LoggerUtils.warn("Nenhum dado retornado de " + endpoint + " para " + project);
                }
            }

            LoggerUtils.success("✅ Concluído download do projeto " + project);
        }
    }

    private JSONArray fetchAll(String project, String endpoint) {
        JSONArray aggregate = new JSONArray();
        Set<String> seen = new HashSet<>();

        final int limit = 100;
        int offset = 0;
        int page = 1;

        try {
            while (true) {
                String url = String.format("%s/%s/%s?limit=%d&offset=%d", BASE, endpoint, project, limit, offset);
                LoggerUtils.step(String.format("🔗 [Página %d] GET %s", page, url));

                long start = System.nanoTime();
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Token", TOKEN);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int status = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                conn.disconnect();

                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                if (status < 200 || status >= 300) {
                    LoggerUtils.warn(String.format("⚠️ HTTP %d ao acessar %s (%s)", status, endpoint, project));
                    MetricsCollector.increment("apiErrors");
                    break;
                }

                JSONObject parsed = new JSONObject(sb.toString());
                JSONArray items = extractArray(parsed);
                if (items == null || items.length() == 0) break;

                int newAdded = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.getJSONObject(i);
                    String id = extractId(obj);
                    if (id == null) id = obj.toString().substring(0, Math.min(32, obj.length()));
                    if (seen.add(id)) {
                        aggregate.put(obj);
                        newAdded++;
                    }
                }

                LoggerUtils.step(String.format("✅ Página %d: %d novos itens (%d totais, %dms)",
                        page, newAdded, aggregate.length(), elapsedMs));

                if (items.length() < limit) break;
                offset += limit;
                page++;
                Thread.sleep(250);
            }

        } catch (Exception e) {
            LoggerUtils.error("Erro ao coletar dados de " + endpoint + "@" + project, e);
        }

        LoggerUtils.success(String.format("📦 %d itens agregados para %s/%s", aggregate.length(), project, endpoint));
        return aggregate;
    }

    private JSONArray extractArray(JSONObject parsed) {
        if (parsed == null) return new JSONArray();
        if (parsed.has("result")) {
            Object res = parsed.get("result");
            if (res instanceof JSONObject) {
                JSONObject ro = (JSONObject) res;
                if (ro.has("entities") && ro.get("entities") instanceof JSONArray)
                    return ro.getJSONArray("entities");
                for (String k : ro.keySet()) {
                    if (ro.get(k) instanceof JSONArray) return ro.getJSONArray(k);
                }
            } else if (res instanceof JSONArray) {
                return (JSONArray) res;
            }
        }
        return new JSONArray();
    }

    private String extractId(JSONObject o) {
        String[] keys = new String[]{"id", "case_id", "result_id", "run_id", "defect_id"};
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) return String.valueOf(o.get(k));
        }
        return null;
    }
}
