package com.sysmap.wellness.report.service;

import com.sysmap.wellness.report.service.kpi.ScopeKPIService;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço legado responsável pelo cálculo de KPIs em contexto de um único projeto.
 *
 * <p>Esta classe foi mantida por compatibilidade com versões anteriores do
 * WellnessQAReporter e ainda é utilizada internamente pelo {@link com.sysmap.wellness.report.service.KPIEngine}
 * para cálculo base de indicadores por release. Embora simplificada em comparação
 * ao novo pipeline multi-release, ela permanece funcional e serve como camada
 * intermediária para cálculo de KPIs específicos, especialmente o KPI de Escopo.</p>
 *
 * <h2>Responsabilidades principais</h2>
 * <ul>
 *   <li>Identificar automaticamente a release ativa de um projeto;</li>
 *   <li>Executar o pipeline básico de cálculo de KPIs para essa release;</li>
 *   <li>Atuar como wrapper para serviços especializados, como {@link ScopeKPIService};</li>
 *   <li>Realizar limpeza e normalização de títulos de Test Plans;</li>
 *   <li>Identificar, ordenar e selecionar releases válidas com base em regex.</li>
 * </ul>
 *
 * <p>Apesar de ser considerado legado, o serviço continua totalmente compatível
 * com o novo modelo multi-release e permanece útil em cenários de processamento
 * simplificado ou de compatibilidade reversa.</p>
 */
public class KPIService {

    private final ScopeKPIService scopeKPI = new ScopeKPIService();

    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("^([A-Z0-9_]+)-(\\d{4})-(\\d{2})-(R\\d{2}).*");

    /**
     * Executa o pipeline de cálculo de KPIs para um único projeto.
     *
     * <p>Fluxo:</p>
     * <ol>
     *   <li>Identifica a release ativa através dos Test Plans;</li>
     *   <li>Caso nenhuma release válida seja encontrada, encerra e retorna lista vazia;</li>
     *   <li>Invoca o {@link ScopeKPIService} para calcular o KPI de Escopo;</li>
     *   <li>Retorna a lista de KPIs calculados (atualmente apenas escopo).</li>
     * </ol>
     *
     * @param consolidated JSON consolidado referente ao projeto.
     * @param project      Nome do projeto sendo avaliado.
     * @return Lista de {@link KPIData} calculados para a release ativa.
     */
    public List<KPIData> calculateKPIs(JSONObject consolidated, String project) {

        LoggerUtils.section("📊 Calculando KPIs — Projeto: " + project);

        List<KPIData> result = new ArrayList<>();

        // Detecta release principal do conjunto de Test Plans
        String releaseId = detectReleaseId(consolidated, project);

        if (releaseId == null) {
            LoggerUtils.warn("⚠ Nenhuma release válida encontrada. KPIs não podem ser calculados.");
            return result;
        }

        LoggerUtils.success("🏷 Release ativa: " + releaseId);

        // Neste serviço legado: apenas 1 KPI (Escopo)
        result.add(scopeKPI.calculate(consolidated, project, releaseId));

        LoggerUtils.success("📦 KPIs calculados: " + result.size());
        return result;
    }

    /**
     * Detecta a release ativa do projeto analisando os títulos dos Test Plans.
     *
     * <p>Processo:</p>
     * <ul>
     *   <li>Itera por todos os Test Plans no consolidated;</li>
     *   <li>Extrai o ID de release de cada título usando o regex {@link #RELEASE_PATTERN};</li>
     *   <li>Coleta todas as releases válidas detectadas;</li>
     *   <li>Ordena as releases encontradas em ordem reversa (mais recente primeiro);</li>
     *   <li>Retorna a release mais recente encontrada.</li>
     * </ul>
     *
     * <p>Se nenhum Test Plan válido for identificado, retorna {@code null}.</p>
     *
     * @param consolidated JSON consolidado contendo os Test Plans.
     * @param project      Nome do projeto (para logs).
     * @return ReleaseId mais recente detectada ou {@code null} se inexistente.
     */
    private String detectReleaseId(JSONObject consolidated, String project) {

        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans == null) return null;

        List<String> releases = new ArrayList<>();

        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;

            String title = p.optString("title", "").trim();
            if (title.isEmpty()) continue;

            String releaseId = extractReleaseId(title);
            if (releaseId != null) releases.add(releaseId);
        }

        if (releases.isEmpty()) {
            LoggerUtils.warn("⚠ Nenhum Test Plan corresponde ao formato de release para " + project);
            return null;
        }

        // Ordenação reversa: R10 > R09 / releases mais recentes primeiro
        releases.sort(Comparator.reverseOrder());

        String latest = releases.get(0);
        LoggerUtils.info("🔎 Releases detectadas: " + releases);
        LoggerUtils.success("➡ Release mais recente selecionada: " + latest);

        return latest;
    }

    /**
     * Extrai um ReleaseId válido a partir de um título de Test Plan,
     * seguindo o padrão do regex definido em {@link #RELEASE_PATTERN}.
     *
     * <p>O método normaliza o texto removendo espaços, padronizando hífens e
     * convertendo para maiúsculas, garantindo maior robustez contra variações
     * de formatação.</p>
     *
     * <p>O formato aceito é, por exemplo:</p>
     *
     * <pre>
     *   PROJ_ABC-2025-03-R01
     *   XPTO-2024-11-R02
     * </pre>
     *
     * @param title Título do Test Plan.
     * @return ReleaseId extraído ou {@code null} caso o título não siga o padrão.
     */
    private String extractReleaseId(String title) {
        if (title == null) return null;

        String clean = title
            .replace("–", "-")
            .replace(" ", "")
            .trim()
            .toUpperCase();

        Matcher m = RELEASE_PATTERN.matcher(clean);
        if (!m.matches()) return null;

        String proj = m.group(1);
        String ano  = m.group(2);
        String mes  = m.group(3);
        String rnn  = m.group(4);

        return proj + "-" + ano + "-" + mes + "-" + rnn;
    }
}
