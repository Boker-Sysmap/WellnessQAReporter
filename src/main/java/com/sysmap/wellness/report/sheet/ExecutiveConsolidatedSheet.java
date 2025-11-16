package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.*;

/**
 * Painel Consolidado FINAL
 *
 * - 2 colunas fixas: Projeto | Release
 * - Uma linha por release (projeto + release)
 * - Usa histórico de KPIs (kpi_results.json)
 * - Número de releases controlado por config.properties:
 *     report.kpi.maxReleases = 0 → todas
 *     report.kpi.maxReleases = 1 → apenas atual
 *     report.kpi.maxReleases = N → N releases mais recentes
 *
 * Por enquanto só exibe o KPI "escopo_planejado".
 */
public class ExecutiveConsolidatedSheet {

    // Por enquanto só este KPI; quando surgirem outros, basta incluir aqui.
    private static final List<String> SELECTED_KPIS = List.of("escopo_planejado");

    // Labels amigáveis para cada KPI
    private static final Map<String, String> KPI_LABELS = Map.of(
        "escopo_planejado", "Escopo planejado"
    );

    public static void create(
        XSSFWorkbook wb,
        Map<String, List<KPIData>> kpisByProject,
        Map<String, String> releaseByProject // hoje não usamos mais, mantido por compat.
    ) {

        Sheet sheet = wb.createSheet("Painel Consolidado");

        // Estilo numérico sem casas
        CellStyle intStyle = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        intStyle.setDataFormat(format.getFormat("0"));

        // 1) Cabeçalho
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Projeto");
        header.createCell(1).setCellValue("Release");

        int colIndex = 2;
        for (String kpiKey : SELECTED_KPIS) {
            String label = KPI_LABELS.getOrDefault(kpiKey, kpiKey);
            header.createCell(colIndex++).setCellValue(label);
        }

        // 2) Config: quantas releases mostrar
        int maxReleases = resolveMaxReleases();

        KPIHistoryRepository historyRepo = new KPIHistoryRepository();
        int rowIndex = 1;

        // Uma “seção” por projeto
        for (String project : kpisByProject.keySet()) {

            // Carrega todo o histórico do projeto
            List<KPIHistoryRecord> history = historyRepo.loadAll(project);

            // Agrupa por release → (kpiKey → último registro)
            Map<String, Map<String, KPIHistoryRecord>> byRelease = new HashMap<>();

            for (KPIHistoryRecord r : history) {

                String kpiKey = r.getKpiName();
                if (!SELECTED_KPIS.contains(kpiKey)) continue;

                String release = r.getRelease();
                if (release == null || release.isBlank()) continue;

                byRelease.computeIfAbsent(release, x -> new HashMap<>());
                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                KPIHistoryRecord existing = kpiMap.get(kpiKey);
                if (existing == null || r.getTimestamp().isAfter(existing.getTimestamp())) {
                    kpiMap.put(kpiKey, r);
                }
            }

            if (byRelease.isEmpty()) {
                // Pode acontecer na primeira execução, sem histórico ainda.
                continue;
            }

            // Lista de releases ordenada da mais recente para a mais antiga
            List<String> releases = new ArrayList<>(byRelease.keySet());
            releases.sort(Comparator.reverseOrder());

            if (maxReleases > 0 && releases.size() > maxReleases) {
                releases = releases.subList(0, maxReleases);
            }

            for (String release : releases) {

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(project);
                row.createCell(1).setCellValue(release);

                colIndex = 2;

                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                for (String kpiKey : SELECTED_KPIS) {
                    Cell cell = row.createCell(colIndex++);

                    KPIHistoryRecord rec = kpiMap.get(kpiKey);
                    if (rec == null || rec.getValue() == null) {
                        cell.setBlank();
                        continue;
                    }

                    double val;
                    try {
                        val = Double.parseDouble(String.valueOf(rec.getValue()));
                    } catch (NumberFormatException e) {
                        val = 0.0;
                    }

                    cell.setCellValue(val);
                    cell.setCellStyle(intStyle);
                }
            }
        }
    }

    /**
     * Lê report.kpi.maxReleases do config.properties.
     * Default = 1 (apenas release atual). 0 = todas.
     */
    private static int resolveMaxReleases() {
        Properties props = new Properties();
        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }

        String raw = props.getProperty("report.kpi.maxReleases", "1").trim();
        try {
            int val = Integer.parseInt(raw);
            return Math.max(0, val);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
