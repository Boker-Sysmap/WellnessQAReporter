package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.*;

/**
 * Aba "Resumo Executivo" – versão PREMIUM.
 *
 * Ajustes incluídos:
 *  - Exibe KPI group (categoria)
 *  - Exibe formattedValue em vez de value cru
 *  - Exibe trendSymbol (↑ ↓ →)
 *  - Formatação profissional
 *  - Ordenação por grupo + nome
 *  - Compatível com Java 8
 *  - Mantém nome da aba igual ao padrão atual
 */
public class ExecutiveKPISheet {

    public static void create(XSSFWorkbook wb, List<KPIData> kpis, String sheetName) {
        LoggerUtils.step("📝 Criando aba Resumo Executivo: " + sheetName);

        Sheet sheet = wb.createSheet(sheetName);

        CellStyle headerStyle = buildHeaderStyle(wb);
        CellStyle groupStyle  = buildGroupStyle(wb);
        CellStyle valueStyle  = buildValueStyle(wb);
        CellStyle normalStyle = buildNormalStyle(wb);

        // Cabeçalhos
        Row header = sheet.createRow(0);
        createHeaderCell(header, 0, "Grupo", headerStyle);
        createHeaderCell(header, 1, "KPI", headerStyle);
        createHeaderCell(header, 2, "Valor", headerStyle);
        createHeaderCell(header, 3, "Tendência", headerStyle);
        createHeaderCell(header, 4, "Descrição", headerStyle);

        // Ordenação dos KPIs: Group → Name
        Collections.sort(kpis, new Comparator<KPIData>() {
            @Override
            public int compare(KPIData a, KPIData b) {
                int g = safe(a.getGroup()).compareTo(safe(b.getGroup()));
                if (g != 0) return g;
                return a.getName().compareTo(b.getName());
            }
        });

        int rowIndex = 1;

        for (KPIData kpi : kpis) {
            Row row = sheet.createRow(rowIndex++);

            // Grupo
            Cell g = row.createCell(0);
            g.setCellValue(safe(kpi.getGroup()));
            g.setCellStyle(groupStyle);

            // Nome
            Cell name = row.createCell(1);
            name.setCellValue(kpi.getName());
            name.setCellStyle(normalStyle);

            // Valor formatado
            Cell v = row.createCell(2);
            v.setCellValue(kpi.getFormattedValue());
            v.setCellStyle(valueStyle);

            // Tendência
            Cell t = row.createCell(3);
            t.setCellValue(kpi.getTrendSymbol());
            t.setCellStyle(normalStyle);

            // Descrição
            Cell d = row.createCell(4);
            d.setCellValue(kpi.getDescription());
            d.setCellStyle(normalStyle);
        }

        // Auto-size
        for (int col = 0; col < 5; col++) {
            try {
                sheet.autoSizeColumn(col);
                int width = sheet.getColumnWidth(col);
                sheet.setColumnWidth(col, Math.min(width + 800, 15000));
            } catch (Exception ignored) {}
        }
    }

    // =====================================================================================
    // HELPERS DE FORMATAÇÃO
    // =====================================================================================

    private static String safe(String s) {
        return (s == null ? "" : s);
    }

    private static void createHeaderCell(Row row, int col, String text, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private static CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(style);
        return style;
    }

    private static CellStyle buildGroupStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLUE.getIndex());
        style.setFont(font);
        applyBorders(style);
        return style;
    }

    private static CellStyle buildValueStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.DARK_GREEN.getIndex());
        style.setFont(font);
        applyBorders(style);
        return style;
    }

    private static CellStyle buildNormalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(false);
        style.setFont(font);
        applyBorders(style);
        return style;
    }

    private static void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
