package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.report.service.engine.KPIEngine;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

import java.util.*;

/**
 * Responsável por executar o {@link KPIEngine}, calcular
 * os KPIs por projeto e determinar a release "principal"
 * de cada projeto para uso nas abas executivas.
 */
public class ReportKpiProcessor {

    private final KPIEngine kpiEngine = new KPIEngine();

    /**
     * Executa o cálculo de KPIs para todos os projetos e
     * constrói a estrutura usada pelas abas executivas.
     *
     * @param consolidatedData Dados consolidados por projeto.
     * @param fallbackRelease  Release padrão baseada no nome do arquivo.
     * @return Estrutura contendo KPIs por projeto e release principal.
     */
    public ReportKpiResult process(
        Map<String, JSONObject> consolidatedData,
        String fallbackRelease
    ) {

        Map<String, List<KPIData>> kpisByProject =
            kpiEngine.calculateForAllProjects(consolidatedData, fallbackRelease);

        LoggerUtils.info("📊 KPIs calculados para " + kpisByProject.size() + " projetos.");

        Map<String, String> releaseByProject =
            buildReleaseByProjectMap(kpisByProject, fallbackRelease);

        return new ReportKpiResult(kpisByProject, releaseByProject);
    }

    /**
     * Determina qual release deve ser considerada “principal”
     * para cada projeto, utilizada pelo Resumo Executivo.
     *
     * <p>Regra atual: pega o primeiro grupo encontrado na lista
     * de KPIs (release associada aos KPIs mais recentes daquela
     * execução). Caso não haja grupo, usa o fallback baseado
     * no nome do arquivo.</p>
     *
     * @param kpisByProject KPIs agrupados por projeto.
     * @param fallback      Release padrão caso nenhuma seja encontrada.
     * @return Mapa projeto → release principal.
     */
    private Map<String, String> buildReleaseByProjectMap(
        Map<String, List<KPIData>> kpisByProject,
        String fallback
    ) {
        Map<String, String> map = new LinkedHashMap<>();

        for (String project : kpisByProject.keySet()) {

            String release =
                kpisByProject.get(project).stream()
                    .filter(k -> k.getGroup() != null && !k.getGroup().isEmpty())
                    .map(KPIData::getGroup)
                    .findFirst()
                    .orElse(fallback);

            map.put(project, release);
        }

        return map;
    }
}
