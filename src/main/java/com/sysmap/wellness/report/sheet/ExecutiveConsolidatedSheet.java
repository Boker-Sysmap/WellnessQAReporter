package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.excel.ExcelStyleFactory;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.*;

public class ExecutiveConsolidatedSheet {

    private static List<String> loadPanelKpis() {
        Properties props = new Properties();

        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        String raw = props.getProperty("panel.kpis", "").trim();
        if (raw.isEmpty()) return Collections.emptyList();

        List<String> list = new ArrayList<>();
        for (String k : raw.split(",")) {
            if (!k.isBlank()) list.add(k.trim());
        }
        return list;
    }

    private static Map<String, String> loadPanelKpiLabels() {
        Properties props = new Properties();

        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        Map<String, String> map = new LinkedHashMap<>();

        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("panel.kpiLabels.")) {
                String key = name.substring("panel.kpiLabels.".length());
                String value = props.getProperty(name);
                map.put(key, value);
            }
        }

        return map;
    }

    public static void create(
        XSSFWorkbook wb,
        Map<String, List<KPIData>> kpisByProject,
        Map<String, String> releaseByProject
    ) {

        Sheet sheet = wb.createSheet("Painel Consolidado");

        // 🔥 NOVO: centralização de estilos
        ExcelStyleFactory styles = new ExcelStyleFactory(wb);

        // ========================================================
        // 1) Cabeçalho
        // ========================================================
        Row header = sheet.createRow(0);
        Cell h1 = header.createCell(0);
        h1.setCellValue("Projeto");
        h1.setCellStyle(styles.header());

        Cell h2 = header.createCell(1);
        h2.setCellValue("Release");
        h2.setCellStyle(styles.header());

        List<String> selectedKpis = loadPanelKpis();
        Map<String, String> labels = loadPanelKpiLabels();

        int colIndex = 2;

        for (String kpiKey : selectedKpis) {
            Cell cell = header.createCell(colIndex++);
            cell.setCellValue(labels.getOrDefault(kpiKey, kpiKey));
            cell.setCellStyle(styles.header());
        }

        // ========================================================
        // 2) Config: quantas releases serão exibidas
        // ========================================================
        int maxReleases = resolveMaxReleases();

        KPIHistoryRepository historyRepo = new KPIHistoryRepository();
        int rowIndex = 1;

        // ========================================================
        // 3) Monta a seção de cada projeto
        // ========================================================
        for (String project : kpisByProject.keySet()) {

            List<KPIHistoryRecord> history = historyRepo.loadAll(project);
            Map<String, Map<String, KPIHistoryRecord>> byRelease = new HashMap<>();

            for (KPIHistoryRecord r : history) {

                String kpiKey = r.getKpiName();
                if (!selectedKpis.contains(kpiKey)) continue;

                String release = r.getRelease();
                if (release == null || release.isBlank()) continue;

                byRelease.computeIfAbsent(release, x -> new HashMap<>());
                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                KPIHistoryRecord existing = kpiMap.get(kpiKey);

                if (existing == null || r.getTimestamp().isAfter(existing.getTimestamp())) {
                    kpiMap.put(kpiKey, r);
                }
            }

            if (byRelease.isEmpty()) continue;

            List<String> releases = new ArrayList<>(byRelease.keySet());
            releases.sort(Comparator.reverseOrder());

            if (maxReleases > 0 && releases.size() > maxReleases) {
                releases = releases.subList(0, maxReleases);
            }

            // ========================================================
            // 4) Preenche linhas
            // ========================================================
            for (String release : releases) {

                Row row = sheet.createRow(rowIndex++);

                Cell pCell = row.createCell(0);
                pCell.setCellValue(project);
                pCell.setCellStyle(styles.text());

                Cell rCell = row.createCell(1);
                rCell.setCellValue(release);
                rCell.setCellStyle(styles.text());

                colIndex = 2;

                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                for (String kpiKey : selectedKpis) {

                    Cell cell = row.createCell(colIndex++);

                    KPIHistoryRecord rec = kpiMap.get(kpiKey);

                    if (rec == null || rec.getValue() == null) {
                        cell.setCellValue("N/A");
                        cell.setCellStyle(styles.text());
                        continue;
                    }

                    // 🔥 Percentuais → formato “0%”
                    boolean isPercent = kpiKey.endsWith("Pct") || kpiKey.contains("Percent");

                    double val;

                    try {
                        val = Double.parseDouble(String.valueOf(rec.getValue()));
                    } catch (NumberFormatException e) {
                        val = 0.0;
                    }

                    cell.setCellValue(val / (isPercent ? 100.0 : 1.0));
                    cell.setCellStyle(isPercent ? styles.numberPercent() : styles.numberInt());
                }
            }
        }
    }

    private static int resolveMaxReleases() {
        Properties props = new Properties();
        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {}

        String raw = props.getProperty("report.kpi.maxReleases", "1").trim();
        try {
            int val = Integer.parseInt(raw);
            return Math.max(0, val);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
