package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Responsável por salvar o histórico RUN-BASED por projeto e release.
 *
 * <p>Para cada projeto/release são gerados:</p>
 * <ul>
 *   <li>Um snapshot do consolidated filtrado pela release;</li>
 *   <li>Um JSON de metadados contendo ano, releaseId, arquivo gerado;</li>
 *   <li>Diretórios organizados por ano/projeto/release.</li>
 * </ul>
 */
public class ReportHistoryWriter {

    /**
     * Salva o histórico RUN-BASED para cada projeto e release detectada.
     * Inclui:
     * <ul>
     *   <li>consolidated filtrado por release;</li>
     *   <li>snapshot da release;</li>
     *   <li>organização por ano/projeto/release;</li>
     * </ul>
     *
     * @param consolidated  Dados completos do consolidate.json.
     * @param finalPath     Caminho do relatório gerado.
     * @param kpisByProject KPIs multi-release calculados.
     */
    public void generateRunBasedHistory(
        Map<String, JSONObject> consolidated,
        Path finalPath,
        Map<String, List<KPIData>> kpisByProject
    ) {
        LoggerUtils.section("📚 Salvando histórico RUN-BASED (multi-release)");

        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());

        for (String project : consolidated.keySet()) {

            List<KPIData> projectKpis = kpisByProject.get(project);
            if (projectKpis == null || projectKpis.isEmpty()) {
                LoggerUtils.warn("⚠ Nenhum KPI encontrado para " + project + " ao salvar histórico.");
                continue;
            }

            // releases distintas presentes nos KPIs
            Set<String> releases = new TreeSet<>(Comparator.reverseOrder());
            for (KPIData k : projectKpis) {
                String g = k.getGroup();
                if (g != null && !g.isEmpty()) {
                    releases.add(g);
                }
            }

            if (releases.isEmpty()) {
                LoggerUtils.warn("⚠ Nenhuma release em KPIs para " + project + " ao salvar histórico.");
                continue;
            }

            for (String releaseId : releases) {

                Path relDir =
                    Paths.get("historico", "releases", normalizeProject(project), year, releaseId);

                Path snapDir =
                    Paths.get("historico", "snapshots", normalizeProject(project), year, releaseId);

                try {
                    Files.createDirectories(relDir);
                    Files.createDirectories(snapDir);

                    JSONObject info = new JSONObject();
                    info.put("project", project);
                    info.put("releaseId", releaseId);
                    info.put("year", year);
                    info.put("generatedAt", now.toString());
                    info.put("reportFile", finalPath.getFileName().toString());

                    // consolidated filtrado por release
                    JSONObject fullConsolidated = consolidated.get(project);
                    JSONObject filteredConsolidated =
                        filterConsolidatedByRelease(fullConsolidated, releaseId);

                    writeJson(filteredConsolidated, snapDir.resolve("consolidated.json"));
                    writeJson(info, relDir.resolve("release_snapshot.json"));

                } catch (Exception e) {
                    LoggerUtils.error("⚠ Falha ao salvar histórico para " + project +
                        " / release " + releaseId, e);
                }
            }
        }
    }

    /**
     * Normaliza o nome do projeto para uso em diretórios.
     */
    private String normalizeProject(String s) {
        return s.toLowerCase()
            .replace(" ", "_")
            .replaceAll("[^a-z0-9_]", "");
    }

    /**
     * Retorna uma versão do consolidated contendo apenas os Test Plans
     * e Test Runs associados à release desejada.
     *
     * <p>Regra:</p>
     * <ul>
     *   <li>Plan: mantido se o título contiver o {@code releaseId}
     *       OU se {@code releaseIdentifier} for exatamente igual;</li>
     *   <li>Run: mantido se {@code releaseIdentifier} for exatamente
     *       igual ao {@code releaseId};</li>
     *   <li>Demais estruturas (cases, suites, defects, run_results)
     *       são preservadas.</li>
     * </ul>
     *
     * @param full      JSON consolidado completo.
     * @param releaseId Identificador da release.
     * @return JSON filtrado apenas para aquela release.
     */
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        if (full == null) return null;

        JSONObject filtered = new JSONObject(full.toString()); // deep clone

        var originalPlans = full.optJSONArray("plan");
        var originalRuns = full.optJSONArray("run");

        var filteredPlans = new org.json.JSONArray();
        var filteredRuns = new org.json.JSONArray();

        // PLAN — compatibilidade com títulos antigos
        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");
                String planRel = p.optString("releaseIdentifier", null);

                // Se já tiver releaseIdentifier, preferir igualdade exata;
                // caso contrário, fallback para title.contains(releaseId)
                if ((planRel != null && planRel.equals(releaseId)) ||
                    (title != null && title.contains(releaseId))) {
                    filteredPlans.put(p);
                }
            }
        }

        // RUN — usa releaseIdentifier
        if (originalRuns != null) {
            for (int i = 0; i < originalRuns.length(); i++) {
                JSONObject r = originalRuns.optJSONObject(i);
                if (r == null) continue;

                String runRel = r.optString("releaseIdentifier", null);

                if (runRel != null && runRel.equals(releaseId)) {
                    filteredRuns.put(r);
                }
            }
        }

        filtered.put("plan", filteredPlans);
        filtered.put("run", filteredRuns);

        return filtered;
    }

    /**
     * Grava qualquer JSON em disco com indentação 2.
     *
     * @param json Objeto JSON a ser salvo.
     * @param path Caminho destino.
     */
    private void writeJson(JSONObject json, Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(json.toString(2));
        } catch (Exception e) {
            LoggerUtils.error("❌ Erro ao salvar JSON em " + path, e);
        }
    }
}
