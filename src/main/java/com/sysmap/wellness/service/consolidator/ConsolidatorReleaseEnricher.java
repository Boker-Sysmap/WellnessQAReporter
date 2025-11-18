package com.sysmap.wellness.service.consolidator;

import com.sysmap.wellness.utils.IdentifierParser;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

public class ConsolidatorReleaseEnricher {

    private static final String UNKNOWN_RELEASE = "UNKNOWN-RELEASE";

    /**
     * Enriquecer PLAN/RUN com:
     * - releaseIdentifier
     * - releaseIdentifierValues
     */
    public void enrich(JSONArray entities,
                       Map<String, JSONObject> releaseMetaById,
                       String context) {

        if (entities == null) return;

        for (int i = 0; i < entities.length(); i++) {

            JSONObject obj = entities.optJSONObject(i);
            if (obj == null) continue;

            String title = obj.optString("title", "").trim();

            String releaseId = UNKNOWN_RELEASE;
            JSONObject valuesJson = new JSONObject();

            if (!title.isEmpty()) {

                try {
                    IdentifierParser.ParsedIdentifier parsed = IdentifierParser.parse(title);

                    if (parsed != null) {
                        releaseId = parsed.getReleaseIdentifier();
                        valuesJson = new JSONObject(parsed.getValues());

                        if (!UNKNOWN_RELEASE.equals(releaseId)) {
                            releaseMetaById.put(releaseId, valuesJson);
                        }

                    } else {
                        LoggerUtils.warn("⚠ Identificador inválido para " + context +
                            " — título: " + title);
                    }

                } catch (Exception ignored) {
                    LoggerUtils.warn("⚠ Erro ao parsear título: " + title);
                }
            }

            obj.put("releaseIdentifier", releaseId);
            obj.put("releaseIdentifierValues", valuesJson);
        }
    }
}
