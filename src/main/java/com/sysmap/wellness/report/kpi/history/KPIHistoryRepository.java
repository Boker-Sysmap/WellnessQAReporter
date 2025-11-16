package com.sysmap.wellness.report.kpi.history;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class KPIHistoryRepository {

    public KPIHistoryRepository() {
        // Nenhuma dependência direta — tudo configurado pelo ConfigManager
    }

    /**
     * Diretório:
     *   {history.kpi.baseDir}/{project}/{release}/kpi_results.json
     */
    private Path getKPIDirectory(String project, String release) {

        String baseDir = ConfigManager.getKPIHistoryBaseDir();
        Path dir = Paths.get(baseDir, project, release);

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao criar diretório de histórico de KPIs: " + dir, e);
        }

        return dir;
    }

    /** Salva os KPIs calculados para um determinado projeto e release. */
    public void save(String project, String releaseName, List<KPIData> results) {

        Path file = getKPIDirectory(project, releaseName).resolve("kpi_results.json");
        JSONArray arr = new JSONArray();

        for (KPIData data : results) {

            JSONObject jsonDetails = data.toJson();

            KPIHistoryRecord record = new KPIHistoryRecord(
                data.getKey(),
                project,
                releaseName,
                LocalDateTime.now(),
                data.getValue(),
                jsonDetails
            );

            arr.put(record.toJson());
        }

        try {
            Files.writeString(
                file,
                arr.toString(2),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gravar histórico de KPIs no arquivo: " + file, e);
        }
    }

    /** Retorna TODOS os arquivos kpi_results.json existentes para um projeto. */
    private List<Path> listAllProjectFiles(String project) {

        String baseDir = ConfigManager.getKPIHistoryBaseDir();
        Path base = Paths.get(baseDir, project);

        if (!Files.exists(base)) {
            return Collections.emptyList();
        }

        try {
            List<Path> files = new ArrayList<>();

            Files.walk(base)
                .filter(f -> f.getFileName().toString().equals("kpi_results.json"))
                .forEach(files::add);

            return files;

        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /** Carrega todos os registros históricos de um projeto. */
    public List<KPIHistoryRecord> loadAll(String project) {

        List<Path> files = listAllProjectFiles(project);
        List<KPIHistoryRecord> result = new ArrayList<>();

        for (Path f : files) {
            try {
                String json = Files.readString(f);
                JSONArray arr = new JSONArray(json);

                for (int i = 0; i < arr.length(); i++) {
                    result.add(KPIHistoryRecord.fromJson(arr.getJSONObject(i)));
                }

            } catch (Exception ignored) {
                // arquivos inválidos são ignorados silenciosamente
            }
        }

        return result;
    }

    public List<KPIHistoryRecord> loadByKPI(String project, String kpiKey) {
        return loadAll(project).stream()
            .filter(r -> kpiKey.equals(r.getKpiName()))
            .sorted(Comparator.comparing(KPIHistoryRecord::getTimestamp))
            .collect(Collectors.toList());
    }

    public Optional<KPIHistoryRecord> loadLast(String project, String kpiKey) {
        List<KPIHistoryRecord> list = loadByKPI(project, kpiKey);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(list.size() - 1));
    }
}
