package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsável por processar e calcular KPIs para cada projeto e para cada release
 * detectada nos dados consolidados provenientes do Qase. Esta classe atua como o
 * “motor” de KPIs do WellnessQAReporter, realizando:
 *
 * <ul>
 *   <li>Detecção automática de releases com base nos títulos dos Test Plans;</li>
 *   <li>Filtragem do consolidated.json para cada release encontrada;</li>
 *   <li>Aplicação da lógica de cálculo de KPIs por meio do {@link KPIService};</li>
 *   <li>Atribuição do identificador de release a cada KPI calculado (via withGroup);</li>
 *   <li>Persistência do histórico por release usando o {@link KPIHistoryService};</li>
 *   <li>Retorno de todos os KPIs calculados, agrupados por projeto.</li>
 * </ul>
 *
 * <p>A arquitetura desta engine permite:</p>
 * <ul>
 *   <li>Processamento multi-release;</li>
 *   <li>Evolução para novos KPIs;</li>
 *   <li>Compatibilidade com diferentes estruturas de dados consolidados;</li>
 *   <li>Geração de histórico temporal para auditorias e comparações.</li>
 * </ul>
 *
 * <p>Este componente é essencial para a geração do Painel Consolidado e para
 * o pipeline RUN-BASED, pois fornece todos os KPIs estruturados por release.</p>
 */
public class KPIEngine {

    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("([A-Z]+-[0-9]{4}-[0-9]{2}-R[0-9]{2})");

    private final KPIHistoryService historyService = new KPIHistoryService();
    private final KPIService kpiService = new KPIService();

    /**
     * Calcula KPIs para TODOS os projetos e TODAS as releases detectadas nos dados
     * consolidados. Para cada projeto:
     *
     * <ol>
     *   <li>Detecta todos os IDs de release presentes nos títulos dos Test Plans;</li>
     *   <li>Filtra o consolidated.json para cada release encontrada;</li>
     *   <li>Aplica o KPIService para calcular indicadores;</li>
     *   <li>Marca cada KPI com a release correspondente (com {@code withGroup});</li>
     *   <li>Registra os resultados no histórico (KPIHistoryService);</li>
     *   <li>Retorna a lista completa de KPIs calculados para o projeto.</li>
     * </ol>
     *
     * @param consolidatedData Mapa contendo o consolidated.json por projeto.
     * @param fallbackRelease  Release usada caso o sistema não consiga detectar nenhuma release.
     * @return Mapa projeto → KPIs de todas as releases detectadas.
     */
    public Map<String, List<KPIData>> calculateForAllProjects(
        Map<String, JSONObject> consolidatedData,
        String fallbackRelease) {

        Map<String, List<KPIData>> result = new LinkedHashMap<>();

        for (String project : consolidatedData.keySet()) {

            JSONObject consolidated = consolidatedData.get(project);

            // Detecção automática de releases
            List<String> releases = detectAllReleaseIds(consolidated, project, fallbackRelease);

            LoggerUtils.info("→ Releases detectadas para " + project + ": " + releases);

            List<KPIData> allKPIs = new ArrayList<>();

            for (String release : releases) {

                LoggerUtils.info("⚙ Calculando KPIs da release " + release);

                // Filtra o consolidated.json apenas para a release
                JSONObject filtered = filterConsolidatedByRelease(consolidated, release);

                // Calcula os KPIs brutos para essa release
                List<KPIData> baseKPIs = kpiService.calculateKPIs(filtered, project);

                // Amarra todos os KPIs ao identificador de release
                List<KPIData> releaseKPIs = new ArrayList<>();

                for (KPIData k : baseKPIs) {
                    releaseKPIs.add(k.withGroup(release));
                }

                // Persiste o histórico por release
                historyService.saveAll(project, release, releaseKPIs);

                // Acumula no resultado final do projeto
                allKPIs.addAll(releaseKPIs);
            }

            result.put(project, allKPIs);
        }

        return result;
    }

    /**
     * Filtra o consolidated.json para que contenha somente os Test Plans que
     * correspondem à release informada. A lógica é simples:
     *
     * <ul>
     *   <li>Cria um clone profundo do consolidated original;</li>
     *   <li>Itera pelos Test Plans;</li>
     *   <li>Inclui somente aqueles cujo título contém o ID da release;</li>
     *   <li>Substitui o array "plan" pelo novo conjunto filtrado.</li>
     * </ul>
     *
     * @param full JSON consolidado completo.
     * @param releaseId Identificador da release a ser mantida.
     * @return JSON consolidado filtrado apenas para a release desejada.
     */
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        JSONObject filtered = new JSONObject(full.toString()); // deep clone seguro

        JSONArray originalPlans = full.optJSONArray("plan");
        JSONArray filteredPlans = new JSONArray();

        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");
                if (title.contains(releaseId)) {
                    filteredPlans.put(p);
                }
            }
        }

        filtered.put("plan", filteredPlans);
        return filtered;
    }

    /**
     * Detecta TODOS os identificadores de release presentes nos títulos dos Test Plans
     * do consolidated.json. O formato reconhecido é:
     *
     * <pre>
     *   ABC-2025-02-R01
     * </pre>
     *
     * <p>Processo:</p>
     * <ul>
     *   <li>Itera por todos os Test Plans do projeto;</li>
     *   <li>Extrai releaseId via expressão regular configurada em RELEASE_PATTERN;</li>
     *   <li>Ordena as releases em ordem reversa (mais recentes primeiro);</li>
     *   <li>Retorna fallback caso nenhuma release válida seja encontrada.</li>
     * </ul>
     *
     * @param consolidated JSON consolidado do projeto.
     * @param project Nome do projeto (usado em logs).
     * @param fallback Release padrão caso nenhuma seja detectada.
     * @return Lista ordenada de releases detectadas (mais recente → mais antiga).
     */
    private List<String> detectAllReleaseIds(JSONObject consolidated,
                                             String project,
                                             String fallback) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null || plans.isEmpty()) {
            return Collections.singletonList(fallback);
        }

        Set<String> releases = new TreeSet<>(Comparator.reverseOrder());

        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;

            String title = plan.optString("title", null);
            String releaseId = extractReleaseId(title);

            if (releaseId != null) releases.add(releaseId);
        }

        if (releases.isEmpty()) releases.add(fallback);

        return new ArrayList<>(releases);
    }

    /**
     * Extrai o ID de release a partir de um título de Test Plan.
     * O padrão aceito é definido por {@link #RELEASE_PATTERN}:
     *
     * <pre>
     *   ([A-Z]+-[0-9]{4}-[0-9]{2}-R[0-9]{2})
     * </pre>
     *
     * Exemplos de títulos compatíveis:
     * <ul>
     *   <li>"Execução ABC-2025-02-R01 - Ciclo de Testes"</li>
     *   <li>"XYZ-2024-11-R02 - Sprint 15"</li>
     * </ul>
     *
     * @param title Título do Test Plan.
     * @return O releaseId detectado ou {@code null} se não houver correspondência.
     */
    private String extractReleaseId(String title) {
        if (title == null) return null;

        Matcher matcher = RELEASE_PATTERN.matcher(title);
        if (matcher.find()) return matcher.group(1);

        return null;
    }
}
