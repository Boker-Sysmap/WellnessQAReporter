package com.sysmap.wellness.service.consolidator;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONObject;

import java.util.*;

public class ConsolidatorGlobalMetadata {

    private static final String UNKNOWN_RELEASE = "UNKNOWN-RELEASE";

    /**
     * Define a release global do projeto.
     * Usa maior release lexicográfica.
     */
    public void applyGlobal(JSONObject projectData,
                            Map<String, JSONObject> releaseMetaById) {

        String globalReleaseId = UNKNOWN_RELEASE;
        JSONObject globalValues = new JSONObject();

        if (!releaseMetaById.isEmpty()) {
            List<String> ids = new ArrayList<>(releaseMetaById.keySet());
            ids.sort(Comparator.reverseOrder());
            globalReleaseId = ids.get(0);
            globalValues = releaseMetaById.get(globalReleaseId);
        } else {
            LoggerUtils.warn("⚠ Nenhuma release válida encontrada → usando UNKNOWN-RELEASE");
        }

        projectData.put("releaseIdentifier", globalReleaseId);
        projectData.put("releaseIdentifierValues", globalValues);
    }
}
