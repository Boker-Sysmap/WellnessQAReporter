package com.sysmap.wellness.report.service.engine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Responsável por identificar releases presentes no consolidated.json.
 *
 * Usa:
 *  - consolidated["releaseIdentifier"] (global)
 *  - run[i]["releaseIdentifier"]
 * Se nenhuma encontrada, usa o fallbackRelease.
 */
public class KPIReleaseDetector {

    public List<String> detectReleases(JSONObject consolidated, String fallbackRelease) {

        Set<String> ids = new TreeSet<>();

        if (consolidated != null) {
            String globalId = consolidated.optString("releaseIdentifier", null);
            if (globalId != null && !globalId.isBlank()
                && !"UNKNOWN-RELEASE".equalsIgnoreCase(globalId)) {
                ids.add(globalId);
            }

            JSONArray runs = consolidated.optJSONArray("run");
            if (runs != null) {
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject r = runs.optJSONObject(i);
                    if (r == null) continue;

                    String rel = r.optString("releaseIdentifier", null);
                    if (rel != null && !rel.isBlank()) {
                        ids.add(rel);
                    }
                }
            }
        }

        if (ids.isEmpty() && fallbackRelease != null && !fallbackRelease.isBlank()) {
            ids.add(fallbackRelease);
        }

        return new ArrayList<>(ids);
    }
}
