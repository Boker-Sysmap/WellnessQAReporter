package com.sysmap.wellness.core.qase.gateway;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;

import java.nio.file.Files;
import java.nio.file.Path;

public class ConsolidatorFileLoader {

    private static final Path JSON_DIR = Path.of("output", "json");
    private final JsonEntityParser parser = new JsonEntityParser();

    /**
     * Carrega um endpoint único (case, suite, defect, plan, run)
     */
    public JSONArray loadEndpoint(String project, String endpoint) {
        try {
            Path file = JSON_DIR.resolve(project + "_" + endpoint + ".json");

            if (!Files.exists(file)) {
                LoggerUtils.warn("⚠ Arquivo não encontrado: " + file);
                return new JSONArray();
            }

            String raw = Files.readString(file).trim();
            if (raw.isBlank()) return new JSONArray();

            JSONArray array = parser.parse(raw);

            LoggerUtils.step("📄 " + file.getFileName() + " → " + array.length() + " registros");

            return array;

        } catch (Exception e) {
            LoggerUtils.error("Erro ao carregar endpoint " + endpoint + "@" + project, e);
            return new JSONArray();
        }
    }
}
