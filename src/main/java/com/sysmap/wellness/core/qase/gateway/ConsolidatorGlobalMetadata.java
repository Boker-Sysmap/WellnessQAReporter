package com.sysmap.wellness.core.qase.gateway;

import org.json.JSONObject;

/**
 * ConsolidatorGlobalMetadata — Versão simplificada
 * -------------------------------------------------
 *
 * Esta classe permanece apenas por compatibilidade estrutural.
 *
 * No pipeline atual, o conceito de “release global do projeto” não é mais
 * utilizado. O agrupamento por release e interpretação de identificadores
 * é responsabilidade exclusiva das camadas posteriores
 * (ReleaseMatcher / KPIEngine / IdentifierParser).
 *
 * Portanto, este componente apenas registra valores neutros
 * e não interfere na lógica de releases.
 */
public class ConsolidatorGlobalMetadata {

    /**
     * Preenche valores neutros de metadata no JSON do projeto,
     * mantendo a mesma interface utilizada anteriormente pelo pipeline.
     *
     * @param projectData  JSON consolidado do projeto.
     * @param releaseMetaById mapa obsoleto, mantido apenas por compatibilidade.
     */
    public void applyGlobal(JSONObject projectData,
                            java.util.Map<String, JSONObject> releaseMetaById) {

        // Valores neutros apenas para preservar o schema.
        projectData.put("releaseIdentifier", JSONObject.NULL);
        projectData.put("releaseIdentifierValues", new JSONObject());
    }
}
