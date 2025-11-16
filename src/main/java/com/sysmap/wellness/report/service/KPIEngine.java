package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Responsável por processar e calcular KPIs para cada projeto e para cada release.
 * Agora inclui:
 *
 * ✔ Regra de congelamento de releases antigas
 * ✔ Processamento apenas:
 *      - da release mais recente, ou
 *      - de releases que ainda não existem no histórico
 * ✔ Logs operacionais claros para o time de QA
 */
public class KPIEngine {

    /**
     * Regex para detecção de identificadores de release nos títulos dos Test Plans.
     *
     * Formato aceito:
     *   {PROJETO}-{ANO}-{MES}-R{NN}
     *
     * Exemplos válidos:
     *   FULLYREPO-2025-02-R01
     *   CHUBB-2024-11-R02
     *   PROJ_ABC-2025-03-R10
     *
     * ✔ Suporta letras maiúsculas, números e underscore no prefixo.
     */
    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("([A-Z0-9_]+-[0-9]{4}-[0-9]{2}-R[0-9]{2})");

    private final KPIHistoryService historyService = new KPIHistoryService();
    private final KPIService kpiService = new KPIService();

    /**
     * Processa todos os projetos e suas respectivas releases, aplicando:
     *
     * <ul>
     *   <li>Detecção automática de releases a partir dos Test Plans;</li>
     *   <li>Consulta ao histórico de KPIs por projeto;</li>
     *   <li>Regra de congelamento:
     *       <ul>
     *           <li>Releases antigas (não mais recentes) não são recalculadas;</li>
     *           <li>Releases não presentes no histórico são calculadas e salvas;</li>
     *           <li>A release mais recente pode ser atualizada.</li>
     *       </ul>
     *   </li>
     *   <li>Chamada ao {@link KPIService} para cálculo dos KPIs da release
     *       (como plannedScope, releaseCoverage);</li>
     *   <li>Gravação do histórico via {@link KPIHistoryService}.</li>
     * </ul>
     *
     * @param consolidatedData Mapa projeto → consolidated.json do Qase.
     * @param fallbackRelease  Identificador de release usado caso nenhuma seja encontrada.
     * @return Mapa projeto → lista de KPIs calculados (multi-release).
     */
    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidatedData,
        String fallbackRelease) {

        Map<String, List<KPIData>> result = new LinkedHashMap<>();

        for (String project : consolidatedData.keySet()) {

            LoggerUtils.info("============================================================");
            LoggerUtils.info("📌 PROCESSANDO PROJETO: " + project);
            LoggerUtils.info("============================================================");

            JSONObject consolidated = consolidatedData.get(project);

            // ---------------------------------------------------------
            // 1) Detectar todas as releases nos Test Plans
            // ---------------------------------------------------------
            LoggerUtils.info("🔎 [RELEASE] Procurando releases nos Test Plans...");
            List<String> detectedReleases = detectAllReleaseIds(consolidated, project, fallbackRelease);
            LoggerUtils.info("🗂️ [RELEASE] Releases detectadas: " + detectedReleases);

            // ---------------------------------------------------------
            // 2) Carregar histórico existente
            // ---------------------------------------------------------
            List<KPIHistoryRecord> history = historyService.getAllHistory(project);

            Set<String> releasesWithHistory = history.stream()
                .map(KPIHistoryRecord::getReleaseName)
                .collect(Collectors.toSet());

            String newestReleaseInHistory = getNewestRelease(history);

            LoggerUtils.info("📚 [HISTORY] Releases presentes no histórico: " + releasesWithHistory);
            LoggerUtils.info("🕒 [HISTORY] Release mais recente registrada: " + newestReleaseInHistory);

            boolean hasAnyHistory = !releasesWithHistory.isEmpty();
            List<KPIData> allKPIs = new ArrayList<>();

            // ---------------------------------------------------------
            // 3) Avaliar cada release detectada
            // ---------------------------------------------------------
            for (String release : detectedReleases) {

                LoggerUtils.info("------------------------------------------------------------");
                LoggerUtils.info("🔎 [RELEASE] Avaliando release: " + release);

                boolean releaseExistsInHistory = releasesWithHistory.contains(release);
                boolean isNewestInHistory = hasAnyHistory && release.equals(newestReleaseInHistory);

                boolean shouldProcess;

                // Caso 1 — Primeira execução (nenhuma release no histórico)
                if (!hasAnyHistory) {
                    LoggerUtils.info(
                        "🆕 [RELEASE] Nenhuma release encontrada no histórico. " +
                            "Primeira execução detectada → TODOS os snapshots serão criados."
                    );
                    shouldProcess = true;
                }

                // Caso 2 — Release ainda não existe no histórico
                else if (!releaseExistsInHistory) {
                    LoggerUtils.info(
                        "🆕 [RELEASE] A release " + release +
                            " não possui snapshot no histórico. " +
                            "Será processada e registrada agora."
                    );
                    shouldProcess = true;
                }

                // Caso 3 — Release existente E é a mais recente → pode atualizar
                else if (isNewestInHistory) {
                    LoggerUtils.info(
                        "♻️ [RELEASE] A release " + release +
                            " é a mais recente no histórico. " +
                            "Será processada e atualizada."
                    );
                    shouldProcess = true;
                }

                // Caso 4 — Release antiga → ignorada
                else {
                    LoggerUtils.info(
                        "⛔ [RELEASE] A release " + release +
                            " já possui snapshot e NÃO é a mais recente. " +
                            "Ela permanecerá CONGELADA e NÃO será processada."
                    );
                    shouldProcess = false;
                }

                if (!shouldProcess) {
                    continue;
                }

                // ---------------------------------------------------------
                // 4) Processar KPIs da release
                // ---------------------------------------------------------
                LoggerUtils.info("⚙ [KPI] Calculando KPIs para a release " + release + "...");

                // Importante: o consolidated filtrado deve conter apenas
                // os dados (plans/runs) relacionados à release em questão,
                // garantindo que KPIs como releaseCoverage não misturem
                // dados de outras releases.
                JSONObject filtered = filterConsolidatedByRelease(consolidated, release);

                List<KPIData> baseKPIs = kpiService.calculateKPIs(filtered, project);

                List<KPIData> releaseKPIs = new ArrayList<>();
                for (KPIData k : baseKPIs) {
                    // withGroup associa a releaseId ao KPIData (usado em histórico e painel).
                    releaseKPIs.add(k.withGroup(release));
                }

                // ---------------------------------------------------------
                // 5) Persistir
                // ---------------------------------------------------------
                LoggerUtils.info("💾 [HISTORY] Gravando KPIs no histórico para a release " + release + "...");
                historyService.saveAll(project, release, releaseKPIs);

                allKPIs.addAll(releaseKPIs);
            }

            result.put(project, allKPIs);
        }

        return result;
    }

    // ========================================================================
    // Filtro por release
    // ========================================================================

    /**
     * Retorna uma versão filtrada do consolidated.json contendo apenas:
     * <ul>
     *   <li>Test Plans cujo título contém o identificador da release;</li>
     *   <li>Test Runs cujo título contém o mesmo identificador;</li>
     * </ul>
     *
     * <p>
     * Isso garante que KPIs como plannedScope e releaseCoverage
     * sejam calculados apenas em cima dos dados da release alvo,
     * evitando misturar execuções de releases anteriores/posteriores.
     * </p>
     *
     * @param full      JSON consolidado completo do projeto.
     * @param releaseId Identificador da release (ex.: FULLY-2025-02-R01).
     * @return JSON filtrado por release.
     */
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        if (full == null) return null;

        // Deep clone simples para não alterar o original.
        JSONObject filtered = new JSONObject(full.toString());

        // -------------------------
        // Filtra Test Plans (plan)
        // -------------------------
        JSONArray originalPlans = full.optJSONArray("plan");
        JSONArray filteredPlans = new JSONArray();

        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");
                if (title != null && title.contains(releaseId)) {
                    filteredPlans.put(p);
                }
            }
        }

        filtered.put("plan", filteredPlans);

        // -------------------------
        // Filtra Test Runs (run)
        // -------------------------
        JSONArray originalRuns = full.optJSONArray("run");
        JSONArray filteredRuns = new JSONArray();

        if (originalRuns != null) {
            for (int i = 0; i < originalRuns.length(); i++) {
                JSONObject r = originalRuns.optJSONObject(i);
                if (r == null) continue;

                String title = r.optString("title", "");
                if (title != null && title.contains(releaseId)) {
                    filteredRuns.put(r);
                }
            }
        }

        filtered.put("run", filteredRuns);

        return filtered;
    }

    // ========================================================================
    // Detecção de releases
    // ========================================================================

    /**
     * Detecta todos os identificadores de release presentes nos títulos dos
     * Test Plans de um consolidated.json.
     *
     * <p>Se nenhuma release válida for detectada, utiliza o fallback
     * informado.</p>
     *
     * @param consolidated JSON consolidado do projeto.
     * @param project      Nome do projeto (usado em logs).
     * @param fallback     Release usada caso nenhuma seja detectada.
     * @return Lista de releases ordenadas da mais recente para a mais antiga.
     */
    private List<String> detectAllReleaseIds(JSONObject consolidated,
                                             String project,
                                             String fallback) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            LoggerUtils.warn("⚠ Nenhum Test Plan encontrado para " + project +
                ". Usando fallback de release: " + fallback);
            return Collections.singletonList(fallback);
        }

        Set<String> releases = new TreeSet<>(Comparator.reverseOrder());

        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            String title = plan.optString("title", null);
            String releaseId = extractReleaseId(title);

            if (releaseId != null) {
                releases.add(releaseId);
            }
        }

        if (releases.isEmpty()) {
            LoggerUtils.warn("⚠ Nenhuma release compatível encontrada nos títulos dos Test Plans de " +
                project + ". Usando fallback: " + fallback);
            releases.add(fallback);
        }

        return new ArrayList<>(releases);
    }

    /**
     * Extrai o ID de release a partir de um título de Test Plan,
     * usando o padrão {@link #RELEASE_PATTERN}.
     *
     * @param title Título do Test Plan.
     * @return releaseId detectado ou {@code null} se não houver match.
     */
    private String extractReleaseId(String title) {
        if (title == null) return null;

        Matcher matcher = RELEASE_PATTERN.matcher(title);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Retorna o identificador da release mais recente, com base na
     * ordenação natural das strings de release.
     *
     * <p>Como o formato é fixo ({PROJETO}-{ANO}-{MES}-R{NN}) e o prefixo
     * do projeto é constante dentro do mesmo projeto, a ordenação
     * lexicográfica funciona adequadamente para definir a mais recente.</p>
     *
     * @param history Lista de registros históricos do projeto.
     * @return Release mais recente ou {@code null} se não houver histórico.
     */
    private String getNewestRelease(List<KPIHistoryRecord> history) {
        if (history == null || history.isEmpty()) return null;

        return history.stream()
            .map(KPIHistoryRecord::getReleaseName)
            .sorted()
            .reduce((a, b) -> b)
            .orElse(null);
    }
}
