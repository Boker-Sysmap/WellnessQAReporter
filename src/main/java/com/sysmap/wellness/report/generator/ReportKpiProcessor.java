package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.report.service.engine.KPIEngine;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.core.kpi.service.KPIReleaseCoverageService;
import com.sysmap.wellness.utils.IdentifierParser;

import java.util.List;
import java.util.Map;

/**
 * Processador auxiliar responsável por executar o KPIEngine isoladamente,
 * usado quando o report precisa gerar apenas KPIs ou validar consistência
 * antes da geração das abas do Excel.
 *
 * Importante:
 *  - O KPIEngine não utiliza mais latestReleaseId.
 *  - Todo o histórico é atualizado livremente, sem congelamento.
 *  - O agrupamento por release é totalmente baseado no IdentifierParser POSICIONAL.
 */
public class ReportKpiProcessor {

    /**
     * Executa o pipeline de KPIs para todos os projetos do consolidated.
     *
     * @param consolidated Mapa PROJECT → consolidated.json já carregado.
     * @return Mapa PROJECT → lista de KPIs resultantes.
     */
    public Map<String, List<KPIData>> process(Map<String, org.json.JSONObject> consolidated) {

        KPIEngine engine = new KPIEngine(
            IdentifierParser::parse,
            List.of(
                new KPIReleaseCoverageService()
                // outros KPIs podem ser registrados aqui (releaseResults, etc.)
            )
        );

        // ✔ Versão final: apenas 1 argumento
        return engine.calculateForAllProjects(consolidated);
    }
}
