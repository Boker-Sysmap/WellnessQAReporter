package com.sysmap.wellness.service.consolidator;

import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ConsolidatorRunResultsLoader {

    private static final Path JSON_DIR = Path.of("output", "json");
    private final JsonEntityParser parser = new JsonEntityParser();

    public Map<String, JSONArray> load(String project) {
        Map<String, JSONArray> map = new LinkedHashMap<>();

        try (DirectoryStream<Path> stream =
                 Files.newDirectoryStream(JSON_DIR, project + "_run_*_results.json")) {

            for (Path file : stream) {

                String name = file.getFileName().toString();
                String runId = extractRunId(name);

                if (runId == null) {
                    LoggerUtils.warn("⚠ Nome inválido para run_results → " + name);
                    continue;
                }

                String raw = Files.readString(file).trim();
                if (raw.isBlank()) continue;

                JSONArray arr = parser.parse(raw);

                LoggerUtils.step("📘 " + name + " → runId=" + runId + " → " + arr.length());
                map.put(runId, arr);
            }

        } catch (IOException e) {
            LoggerUtils.error("Erro ao carregar run_results de " + project, e);
        }

        return map;
    }

    private String extractRunId(String filename) {
        try {
            String[] parts = filename.split("_");
            if (parts.length < 4) return null;
            String id = parts[2];
            if (id.contains(".")) id = id.substring(0, id.indexOf('.'));
            return id;
        } catch (Exception e) {
            return null;
        }
    }
}
