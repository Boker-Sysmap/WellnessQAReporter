package com.sysmap.wellness.core.excel.sheet;

import com.sysmap.wellness.core.excel.styles.ExcelStyleFactory;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Aba: Painel Consolidado
 *
 * Baseado nos objetos ReleaseSummaryRow armazenados em:
 * consolidatedData[project].releaseSummaries[]
 */
public class ExecutiveConsolidatedSheet {

    public static void create(
        XSSFWorkbook wb,
        Map<String, JSONObject> consolidatedData
    ) {

        LoggerUtils.section("📊 Criando aba: Painel Consolidado (via ReleaseSummaryRow)");

        Sheet sheet = wb.createSheet("Painel Consolidado");
        ExcelStyleFactory styles = new ExcelStyleFactory(wb);

        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);

        int col = 0;
        header.createCell(col++).setCellValue("Projeto");
        header.createCell(col++).setCellValue("Release");

        header.createCell(col++).setCellValue("Escopo Planejado");
        header.createCell(col++).setCellValue("Casos Executados");
        header.createCell(col++).setCellValue("Cobertura (%)");

        header.createCell(col++).setCellValue("Passed (%)");
        header.createCell(col++).setCellValue("Failed (%)");
        header.createCell(col++).setCellValue("Blocked (%)");
        header.createCell(col++).setCellValue("Skipped (%)");
        header.createCell(col++).setCellValue("Retest (%)");

        // ============================================================
        // Para cada projeto → consumir releaseSummaries
        // ============================================================
        for (String project : consolidatedData.keySet()) {

            JSONObject root = consolidatedData.get(project);
            if (root == null) continue;

            JSONArray arr = root.optJSONArray("releaseSummaries");
            if (arr == null || arr.isEmpty()) continue;

            Set<String> releasesSorted = new LinkedHashSet<>();

            // Primeiro: coletar releases já ordenadas
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                releasesSorted.add(row.optString("releaseId"));
            }

            // Segundo: imprimir cada linha
            for (String releaseId : releasesSorted) {

                // localizar o objeto resumo
                JSONObject summary = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject temp = arr.getJSONObject(i);
                    if (releaseId.equals(temp.optString("releaseId"))) {
                        summary = temp;
                        break;
                    }
                }
                if (summary == null) continue;

                Row row = sheet.createRow(rowIndex++);
                int c = 0;

                row.createCell(c++).setCellValue(project);
                row.createCell(c++).setCellValue(releaseId);

                row.createCell(c++).setCellValue(summary.optInt("plannedScope", 0));
                row.createCell(c++).setCellValue(summary.optInt("executedCases", 0));
                row.createCell(c++).setCellValue(summary.optDouble("coveragePct", 0.0));

                row.createCell(c++).setCellValue(summary.optDouble("passedPct", 0.0));
                row.createCell(c++).setCellValue(summary.optDouble("failedPct", 0.0));
                row.createCell(c++).setCellValue(summary.optDouble("blockedPct", 0.0));
                row.createCell(c++).setCellValue(summary.optDouble("skippedPct", 0.0));
                row.createCell(c++).setCellValue(summary.optDouble("retestPct", 0.0));
            }
        }

        // ============================================================
        // FORMATAÇÃO COMPLETA DA PLANILHA
        // ============================================================
        applyFormatting(sheet, styles);

        LoggerUtils.success("✔ Painel Consolidado criado com sucesso.");
    }

    // ========================================================================
    // MÉTODO DE FORMATAÇÃO
    // ========================================================================
    private static void applyFormatting(Sheet sheet, ExcelStyleFactory styles) {

        int lastRow = sheet.getLastRowNum();

        // CABEÇALHO
        Row header = sheet.getRow(0);
        if (header != null) {
            for (Cell cell : header) {
                cell.setCellStyle(styles.header());
            }
        }

        // LINHAS DE DADOS
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            boolean zebra = (r % 2 == 0);

            for (Cell cell : row) {
                CellStyle style;

                if (cell.getCellType() == CellType.NUMERIC) {
                    double v = cell.getNumericCellValue();

                    if (v >= 0 && v <= 1) {
                        style = zebra ? styles.zebraPercent() : styles.numberPercent();
                    } else {
                        style = zebra ? styles.zebraInt() : styles.numberInt();
                    }

                } else {
                    style = styles.text();
                }

                cell.setCellStyle(style);
            }
        }

        // AUTO-FILTRO
        int cols = header.getLastCellNum();
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, cols - 1));

        // FREEZE HEADER
        sheet.createFreezePane(0, 1);

        // LARGURA DE COLUNAS
        for (int c = 0; c < cols; c++) {
            if (c <= 1) {
                sheet.setColumnWidth(c, 30 * 256);
            } else {
                sheet.setColumnWidth(c, 15 * 256);
            }
        }
    }
}
