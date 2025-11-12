package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.datetime.BusinessTimeCalculator;
import com.sysmap.wellness.utils.datetime.WorkSchedule;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gera a planilha "Gestão de Defeitos - Analítico" com formatação padronizada.
 * Agora com nome de aba dinâmico por projeto.
 */
public class DefectAnalyticalReportSheet {
    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final WorkSchedule workSchedule = new WorkSchedule();
    private static final BusinessTimeCalculator businessTimeCalculator =
            new BusinessTimeCalculator(workSchedule);

    public void create(Workbook wb, Map<String, JSONArray> dataByProject, String sheetName) {
        LoggerUtils.step("🐞 Criando planilha: " + sheetName);

        Sheet sheet = wb.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(wb);
        int rowNum = 0;

        String[] headers = {
                "Projeto", "Funcionalidade", "Título", "Ticket",
                "Status", "Severidade", "Criado em",
                "Reportado por", "Reportado em", "Tempo em aberto", "Resolvido em", "Tempo de Resolução"
        };

        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }

        int totalDefects = 0;
        List<String> projects = new ArrayList<>(dataByProject.keySet());
        Collections.sort(projects);

        for (String projectCode : projects) {
            JSONArray defectsArray = dataByProject.get(projectCode);
            if (defectsArray == null || defectsArray.isEmpty()) continue;

            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                createStyledCell(row, col++, projectCode, styles.get("left"));
                createStyledCell(row, col++, defect.optString("suite", "Não identificada"), styles.get("left"));
                createStyledCell(row, col++, defect.optString("title", ""), styles.get("left"));
                createStyledCell(row, col++, defect.optString("ticket", "N/A"), styles.get("left"));
                createStyledCell(row, col++, defect.optString("status", ""), styles.get("center"));
                createStyledCell(row, col++, defect.optString("severity", ""), styles.get("center"));
                createStyledCell(row, col++, formatDate(defect.optString("created_at", "")), styles.get("center"));
                createStyledCell(row, col++, defect.optString("reported_by", "Desconhecido"), styles.get("left"));
                createStyledCell(row, col++, formatDate(defect.optString("report_date_iso", "")), styles.get("center"));

                String reportDateIso = defect.optString("report_date_iso", "");
                String resolvedAtIso = defect.optString("resolved_at", "");
                String openTime = "";
                if ((resolvedAtIso == null || resolvedAtIso.isBlank())
                        && reportDateIso != null && !reportDateIso.isBlank()) {
                    LocalDateTime start = parseIsoToLocalDateTime(reportDateIso);
                    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                    if (start != null && now.isAfter(start)) {
                        openTime = businessTimeCalculator.calculateBusinessTime(start, now);
                    }
                }
                createStyledCell(row, col++, openTime, styles.get("center"));
                createStyledCell(row, col++, formatDate(resolvedAtIso), styles.get("center"));

                String resolutionTime = "";
                if (resolvedAtIso != null && !resolvedAtIso.isBlank()
                        && reportDateIso != null && !reportDateIso.isBlank()) {
                    resolutionTime = calculateDeltaHHmm(reportDateIso, resolvedAtIso);
                }
                createStyledCell(row, col++, resolutionTime, styles.get("center"));

                totalDefects++;
            }
        }

        ReportStyleManager.autoSizeColumnsWithPadding(sheet, headers.length, ReportStyleManager.getDefaultPadding());
        LoggerUtils.success("📊 Planilha '" + sheetName + "' criada (" + totalDefects + " registros).");
    }

    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            LocalDateTime ldt = odt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return ldt.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return "";
        }
    }

    private String calculateDeltaHHmm(String startIso, String endIso) {
        try {
            LocalDateTime start = parseIsoToLocalDateTime(startIso);
            LocalDateTime end = parseIsoToLocalDateTime(endIso);
            if (start == null || end == null) return "";
            if (end.isBefore(start)) return "00:00";
            return businessTimeCalculator.calculateBusinessTime(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    private LocalDateTime parseIsoToLocalDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(iso);
            return odt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
