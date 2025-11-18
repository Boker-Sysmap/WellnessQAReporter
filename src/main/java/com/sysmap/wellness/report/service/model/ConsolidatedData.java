package com.sysmap.wellness.report.service.model;

import com.sysmap.wellness.service.consolidator.DataConsolidator;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h1>ConsolidatedData – Estrutura Unificada de Dados de Projeto</h1>
 *
 * <p>
 * Esta classe representa o resultado final do processo de consolidação do Qase
 * (executado pelo {@link DataConsolidator}).
 * </p>
 *
 * <p>
 * Além das estruturas core (cases, suites, defects, runs, run_results),
 * agora inclui também:
 * </p>
 *
 * <ul>
 *     <li><b>releaseIdentifier</b> – identificador final da release,
 *         montado dinamicamente a partir de mnemônicos;</li>
 *     <li><b>releaseIdentifierValues</b> – mapa contendo cada mnemônico
 *         e seu valor original do JSON;</li>
 * </ul>
 *
 * <p>
 * Esses campos são fundamentais para o pipeline multi-release,
 * o histórico de KPIs e o agrupamento lógico nos relatórios.
 * </p>
 */
public class ConsolidatedData {

    private JSONObject rootJson;

    /** Identificador final da release (ex.: S7_1.2.3_prod_IOS_PT_funcional_manual). */
    private String releaseIdentifier;

    /** Valores brutos usados para montar o identificador. */
    private Map<String, String> releaseIdentifierValues = new LinkedHashMap<>();

    // =====================================================================
    //  JSON PRINCIPAL
    // =====================================================================

    public JSONObject getRootJson() {
        return rootJson;
    }

    public void setRootJson(JSONObject rootJson) {
        this.rootJson = rootJson;
    }

    // =====================================================================
    //  RELEASE IDENTIFIER
    // =====================================================================

    public String getReleaseIdentifier() {
        return releaseIdentifier;
    }

    public void setReleaseIdentifier(String releaseIdentifier) {
        this.releaseIdentifier = releaseIdentifier;
    }

    public Map<String, String> getReleaseIdentifierValues() {
        return Collections.unmodifiableMap(releaseIdentifierValues);
    }

    public void setReleaseIdentifierValues(Map<String, String> releaseIdentifierValues) {
        this.releaseIdentifierValues.clear();
        if (releaseIdentifierValues != null) {
            this.releaseIdentifierValues.putAll(releaseIdentifierValues);
        }
    }
}
