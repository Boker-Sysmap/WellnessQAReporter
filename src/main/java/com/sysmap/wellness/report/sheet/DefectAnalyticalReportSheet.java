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
        List<String> projects = new ArrayList<String>(dataByProject.keySet());
        Collections.sort(projects);

        for (String projectCode : projects) {
            JSONArray defectsArray = dataByProject.get(projectCode);
            if (defectsArray == null || defectsArray.length() == 0) continue;

            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // Projeto
                createStyledCell(row, col++, projectCode, styles.get("left"));
                // Funcionalidade
                createStyledCell(row, col++, defect.optString("suite", "Não identificada"), styles.get("left"));
                // Título
                createStyledCell(row, col++, defect.optString("title", ""), styles.get("left"));
                // Ticket
                createStyledCell(row, col++, defect.optString("ticket", "N/A"), styles.get("left"));
                // Status
                createStyledCell(row, col++, defect.optString("status", ""), styles.get("center"));
                // Severidade
                createStyledCell(row, col++, defect.optString("severity", ""), styles.get("center"));
                // Criado em
                createStyledCell(row, col++, formatDate(defect.optString("created_at", "")), styles.get("center"));
                // Reportado por
                createStyledCell(row, col++, defect.optString("reported_by", "Desconhecido"), styles.get("left"));

                // Reportado em (usa report_date_iso)
                String reportDateIso = defect.optString("report_date_iso", "");
                createStyledCell(row, col++, formatDate(reportDateIso), styles.get("center"));

                // Resolvido em (data resolvida)
                String resolvedAtIso = defect.optString("resolved_at", "");

                // Tempo em aberto – prioriza valor vindo do serviço (open_time)
                String openTime = defect.optString("open_time", "");
                if (openTime == null || openTime.trim().isEmpty()) {
                    if ((resolvedAtIso == null || resolvedAtIso.trim().isEmpty()) &&
                            reportDateIso != null && !reportDateIso.trim().isEmpty()) {

                        LocalDateTime start = parseIsoToLocalDateTime(reportDateIso);
                        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

                        if (start != null && now.isAfter(start)) {
                            openTime = businessTimeCalculator.calculateBusinessTime(start, now);
                        }
                    }
                }
                createStyledCell(row, col++, openTime, styles.get("center"));

                // Resolvido em (data)
                createStyledCell(row, col++, formatDate(resolvedAtIso), styles.get("center"));

                // Tempo de resolução – prioriza valor vindo do serviço (resolution_time)
                String resolutionTime = defect.optString("resolution_time", "");
                if (resolutionTime == null || resolutionTime.trim().isEmpty()) {
                    if (resolvedAtIso != null && !resolvedAtIso.trim().isEmpty() &&
                            reportDateIso != null && !reportDateIso.trim().isEmpty()) {

                        resolutionTime = calculateDeltaHHmm(reportDateIso, resolvedAtIso);
                    }
                }
                createStyledCell(row, col++, resolutionTime, styles.get("center"));

                totalDefects++;
            }
        }

        ReportStyleManager.autoSizeColumnsWithPadding(
                sheet,
                headers.length,
                ReportStyleManager.getDefaultPadding()
        );
        LoggerUtils.success("📊 Planilha '" + sheetName + "' criada (" + totalDefects + " registros).");
    }

    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            LocalDateTime ldt = odt.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
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
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(iso);
            return odt.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
