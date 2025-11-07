package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.util.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Cria a planilha "Gestão de Defeitos - Analítico"
 * Lê o conteúdo de defect.json / defects.json e gera uma aba detalhada por projeto.
 */
public class DefectAnalyticalReportSheet {

    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public void create(Workbook wb, Map<String, JSONArray> dataByProject) {
        LoggerUtils.step("🐞 Criando planilha: Gestão de Defeitos - Analítico");

        Sheet sheet = wb.createSheet("Gestão de Defeitos - Analítico");
        int rowNum = 0;

        // Cabeçalhos
        String[] headers = {"Projeto", "ID", "Título", "Status", "Severidade", "Criado Por",
                "Criado em", "Atualizado em", "Resolvido em", "Tempo de Resolução", "Descrição"};
        Row header = sheet.createRow(rowNum++);
        CellStyle headerStyle = wb.createCellStyle();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int totalDefects = 0;

        // Itera pelos projetos
        for (var entry : dataByProject.entrySet()) {
            String projectCode = entry.getKey();
            JSONArray defectsArray = entry.getValue();

            if (defectsArray == null || defectsArray.isEmpty()) continue;

            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                String status = defect.optString("status", "");
                String resolvedAt = defect.optString("resolved_at", "");
                String createdAt = defect.optString("created_at", "");
                String updatedAt = defect.optString("updated_at", "");

                // Preenche colunas
                row.createCell(col++).setCellValue(projectCode);
                row.createCell(col++).setCellValue(defect.opt("id") != null ? defect.get("id").toString() : "");
                row.createCell(col++).setCellValue(defect.optString("title", ""));
                row.createCell(col++).setCellValue(status);
                row.createCell(col++).setCellValue(defect.optString("severity", ""));
                row.createCell(col++).setCellValue(defect.optString("created_by", ""));
                row.createCell(col++).setCellValue(formatDate(createdAt));
                row.createCell(col++).setCellValue(formatDate(updatedAt));

                // ✅ Resolvido em (somente se status = resolved)
                if ("resolved".equalsIgnoreCase(status) && !resolvedAt.isEmpty()) {
                    row.createCell(col++).setCellValue(formatDate(resolvedAt));
                } else {
                    row.createCell(col++).setCellValue("");
                }

                // 🕓 Tempo de Resolução
                row.createCell(col++).setCellValue(calculateResolutionTime(createdAt, resolvedAt));

                row.createCell(col++).setCellValue(defect.optString("description", ""));
                totalDefects++;
            }

            LoggerUtils.success("📁 Projeto " + projectCode + " com " + defectsArray.length() + " defeitos carregados.");
        }

        for (int c = 0; c < headers.length; c++) {
            sheet.autoSizeColumn(c);
        }

        LoggerUtils.success("📊 Planilha 'Gestão de Defeitos - Analítico' criada (" + totalDefects + " registros).");
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            return odt.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return isoDate;
        }
    }

    private String calculateResolutionTime(String createdIso, String resolvedIso) {
        if (createdIso == null || resolvedIso == null || createdIso.isEmpty() || resolvedIso.isEmpty())
            return "";
        try {
            OffsetDateTime start = OffsetDateTime.parse(createdIso);
            OffsetDateTime end = OffsetDateTime.parse(resolvedIso);
            Duration d = Duration.between(start, end);
            long days = d.toDays();
            long hours = d.toHours() % 24;
            long minutes = d.toMinutes() % 60;
            return String.format("%dd %02dh %02dm", days, hours, minutes);
        } catch (Exception e) {
            return "";
        }
    }
}
