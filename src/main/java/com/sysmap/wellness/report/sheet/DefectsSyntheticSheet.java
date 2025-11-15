package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aba "Defeitos Sintético" - visão resumida e tabular dos defeitos do projeto.
 * Exibe totais por severidade, status, módulo e tempo médio de resolução.
 * Compatível com Java 11 e Apache POI 5.4.1.
 */
public class DefectsSyntheticSheet {

    /**
     * Cria a planilha "Defeitos Sintético" para um projeto específico.
     *
     * @param wb          Workbook ativo
     * @param defectsData JSON contendo os defeitos do projeto
     * @param sheetName   Nome completo da aba (ex: "APP01 – Defeitos Sintético")
     */
    public static Sheet create(XSSFWorkbook wb, JSONObject defectsData, String sheetName) {
        Sheet sheet = wb.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(wb);
        int rowIdx = 0;

        LoggerUtils.step("📄 Criando aba: " + sheetName);

        // === 🔹 Título principal ===
        Row titleRow = sheet.createRow(rowIdx++);
        Cell title = titleRow.createCell(0);
        title.setCellValue("Resumo Sintético de Defeitos");
        title.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null || defects.isEmpty()) {
            Row empty = sheet.createRow(rowIdx++);
            empty.createCell(0).setCellValue("Nenhum defeito registrado neste projeto.");
            empty.getCell(0).setCellStyle(styles.get("label"));
            return sheet;
        }

        // === 📊 1. Totais gerais ===
        Map<String, Object> totals = calculateTotals(defects);
        rowIdx = createTotalsSection(sheet, styles, totals, rowIdx + 2);

        // === 📈 2. Tabelas agrupadas ===
        rowIdx += 1;
        rowIdx = createTable(sheet, styles, groupBy(defects, "severity", "Severidade"), "Distribuição por Severidade", rowIdx);
        rowIdx = createTable(sheet, styles, groupBy(defects, "status", "Status"), "Distribuição por Status", rowIdx + 2);
        rowIdx = createTable(sheet, styles, groupBy(defects, "component", "Módulo"), "Top 10 Módulos Afetados", rowIdx + 2);

        // === 📆 3. Tabela de tempos médios de resolução ===
        rowIdx += 2;
        rowIdx = createResolutionTable(sheet, styles, defects, rowIdx);

        for (int i = 0; i <= 8; i++) sheet.autoSizeColumn(i);

        LoggerUtils.success("✅ Aba '" + sheetName + "' criada com sucesso.");
        return sheet;
    }

    // ======================================================
    // 📊 Totais gerais
    // ======================================================
    private static Map<String, Object> calculateTotals(JSONArray defects) {
        long total = defects.length();
        long open = 0, closed = 0, reopened = 0;
        double totalResolutionDays = 0;
        int resolvedCount = 0;

        DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;
        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            String status = d.optString("status", "").toLowerCase(Locale.ROOT);
            if (status.contains("open")) open++;
            else if (status.contains("closed")) closed++;
            else if (status.contains("reopen")) reopened++;

            String created = d.optString("created_at", null);
            String closedAt = d.optString("closed_at", null);
            if (created != null && closedAt != null) {
                try {
                    LocalDateTime c1 = LocalDateTime.parse(created, fmt);
                    LocalDateTime c2 = LocalDateTime.parse(closedAt, fmt);
                    totalResolutionDays += java.time.Duration.between(c1, c2).toHours() / 24.0;
                    resolvedCount++;
                } catch (Exception ignored) {}
            }
        }

        double closureRate = total > 0 ? (closed * 100.0 / total) : 0;
        double reopenRate = total > 0 ? (reopened * 100.0 / total) : 0;
        double avgResolution = resolvedCount > 0 ? totalResolutionDays / resolvedCount : 0;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Total de Defeitos", total);
        map.put("Abertos", open);
        map.put("Fechados", closed);
        map.put("Reabertos", reopened);
        map.put("Taxa de Fechamento (%)", Math.round(closureRate));
        map.put("Reabertura (%)", Math.round(reopenRate));
        map.put("Tempo Médio Resolução (dias)", Math.round(avgResolution));

        return map;
    }

    private static int createTotalsSection(Sheet sheet, ReportStyleManager styles, Map<String, Object> totals, int rowIdx) {
        Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("Totais Gerais");
        header.getCell(0).setCellStyle(styles.get("subtitle"));

        for (Map.Entry<String, Object> entry : totals.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(String.valueOf(entry.getValue()));
            row.getCell(0).setCellStyle(styles.get("label"));
            row.getCell(1).setCellStyle(styles.get("value"));
        }
        return rowIdx;
    }

    // ======================================================
    // 📈 Tabelas agrupadas (por severidade, status, módulo)
    // ======================================================
    private static Map<String, Long> groupBy(JSONArray defects, String field, String defaultLabel) {
        Map<String, Long> map = new HashMap<>();
        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            String key = d.optString(field, defaultLabel);
            map.put(key, map.getOrDefault(key, 0L) + 1);
        }
        return map;
    }

    private static int createTable(Sheet sheet, ReportStyleManager styles, Map<String, Long> data, String title, int rowIdx) {
        if (data.isEmpty()) return rowIdx;

        Row titleRow = sheet.createRow(rowIdx++);
        titleRow.createCell(0).setCellValue(title);
        titleRow.getCell(0).setCellStyle(styles.get("subtitle"));

        Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("Categoria");
        header.createCell(1).setCellValue("Quantidade");
        header.getCell(0).setCellStyle(styles.get("header"));
        header.getCell(1).setCellStyle(styles.get("header"));

        List<Map.Entry<String, Long>> sorted = data.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        for (Map.Entry<String, Long> entry : sorted) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
            row.getCell(0).setCellStyle(styles.get("value"));
            row.getCell(1).setCellStyle(styles.get("value"));
        }

        return rowIdx;
    }

    // ======================================================
    // ⏱️ Tempo médio de resolução
    // ======================================================
    private static int createResolutionTable(Sheet sheet, ReportStyleManager styles, JSONArray defects, int rowIdx) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;
        List<Double> durations = new ArrayList<>();

        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            String created = d.optString("created_at", null);
            String closed = d.optString("closed_at", null);
            if (created != null && closed != null) {
                try {
                    LocalDateTime c1 = LocalDateTime.parse(created, fmt);
                    LocalDateTime c2 = LocalDateTime.parse(closed, fmt);
                    durations.add(java.time.Duration.between(c1, c2).toHours() / 24.0);
                } catch (Exception ignored) {}
            }
        }

        Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("Análise de Tempo Médio de Resolução (em dias)");
        header.getCell(0).setCellStyle(styles.get("subtitle"));

        if (durations.isEmpty()) {
            Row empty = sheet.createRow(rowIdx++);
            empty.createCell(0).setCellValue("Nenhum defeito resolvido para cálculo de média.");
            empty.getCell(0).setCellStyle(styles.get("label"));
            return rowIdx;
        }

        double avg = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double max = durations.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = durations.stream().mapToDouble(Double::doubleValue).min().orElse(0);

        Row headerRow = sheet.createRow(rowIdx++);
        headerRow.createCell(0).setCellValue("Métrica");
        headerRow.createCell(1).setCellValue("Valor (dias)");
        headerRow.getCell(0).setCellStyle(styles.get("header"));
        headerRow.getCell(1).setCellStyle(styles.get("header"));

        Object[][] rows = {
                {"Média de Resolução", Math.round(avg)},
                {"Menor Tempo", Math.round(min)},
                {"Maior Tempo", Math.round(max)}
        };

        for (Object[] r : rows) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(r[0].toString());
            row.createCell(1).setCellValue(String.valueOf(r[1]));
            row.getCell(0).setCellStyle(styles.get("value"));
            row.getCell(1).setCellStyle(styles.get("value"));
        }

        return rowIdx;
    }
}
