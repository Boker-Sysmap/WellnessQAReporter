package com.sysmap.wellness.core.kpi.engine;

import com.sysmap.wellness.utils.ReleaseUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Responsável por determinar o releaseId efetivo a ser usado
 * pelo KPIService, a partir do consolidated filtrado.
 */
public class KPIReleaseExtractor {

    private static final String UNKNOWN = "UNKNOWN-RELEASE";

    public String extractRelease(JSONObject consolidated) {
        if (consolidated == null) return UNKNOWN;

        // 1) Tentar usar releaseIdentifier global
        String global = consolidated.optString("releaseIdentifier", null);
        if (global != null && !global.isBlank()
            && !"UNKNOWN-RELEASE".equalsIgnoreCase(global)) {
            return global;
        }

        // 2) Fallback: tentar extrair de um Test Plan via ReleaseUtils
        JSONArray plans = consolidated.optJSONArray("plan");
        if (plans != null && !plans.isEmpty()) {
            JSONObject p = plans.optJSONObject(0);
            if (p != null) {
                String title = p.optString("title", "");
                String detected = ReleaseUtils.extractReleaseIdFromTitle(title);
                if (detected != null && !detected.isBlank()) {
                    return detected;
                }
            }
        }

        return UNKNOWN;
    }
}
