package com.sysmap.wellness.service.consolidator;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class JsonEntityParser {

    /**
     * Parser tolerante de JSON (aceita múltiplos formatos vindos do Qase)
     */
    public JSONArray parse(String raw) {
        try {
            raw = raw.trim();

            if (raw.startsWith("[")) return new JSONArray(raw);

            JSONObject root = new JSONObject(raw);

            if (root.has("result")) {
                Object r = root.get("result");

                if (r instanceof JSONObject) {
                    JSONObject ro = (JSONObject) r;

                    if (ro.has("entities") && ro.get("entities") instanceof JSONArray)
                        return ro.getJSONArray("entities");

                    for (String k : ro.keySet())
                        if (ro.get(k) instanceof JSONArray)
                            return ro.getJSONArray(k);
                }

                if (r instanceof JSONArray) return (JSONArray) r;
            }

            for (String k : root.keySet())
                if (root.get(k) instanceof JSONArray)
                    return root.getJSONArray(k);

        } catch (Exception e) {
            LoggerUtils.warn("⚠ JSON inválido — retornando array vazio");
        }

        return new JSONArray();
    }
}
