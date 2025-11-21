package com.sysmap.wellness.core.excel.styles;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.HashMap;
import java.util.Map;

/**
 * Fábrica centralizada de estilos para todas as planilhas Excel do
 * WellnessQAReporter.
 *
 * Essa classe garante:
 *  - Consistência visual entre todas as abas do relatório.
 *  - Reuso eficiente de estilos (POI tem limite ~64k estilos).
 *  - Facilidade de manutenção caso o padrão visual mude.
 */
public class ExcelStyleFactory {

    private final XSSFWorkbook wb;
    private final Map<String, CellStyle> cache = new HashMap<>();

    public ExcelStyleFactory(XSSFWorkbook workbook) {
        this.wb = workbook;
    }

    // ============================================================
    // Estilos PRINCIPAIS
    // ============================================================

    /** Cabeçalho padrão (fundo cinza, texto branco, centralizado, bold). */
    public CellStyle header() {
        return cache.computeIfAbsent("header", k -> {
            CellStyle style = wb.createCellStyle();

            style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setFontHeightInPoints((short) 11);
            style.setFont(font);

            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);

            addBorders(style);
            return style;
        });
    }

    /** Texto padrão centralizado. */
    public CellStyle text() {
        return cache.computeIfAbsent("text", k -> {
            CellStyle style = wb.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            addBorders(style);
            return style;
        });
    }

    /** Número inteiro formato "0". */
    public CellStyle numberInt() {
        return cache.computeIfAbsent("numberInt", k -> {
            CellStyle style = wb.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(wb.createDataFormat().getFormat("0"));
            addBorders(style);
            return style;
        });
    }

    /** Porcentagem formato "0%". */
    public CellStyle numberPercent() {
        return cache.computeIfAbsent("numberPercent", k -> {
            CellStyle style = wb.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(wb.createDataFormat().getFormat("0%"));
            addBorders(style);
            return style;
        });
    }

    /** Zebra-striping (cinza leve + número). */
    public CellStyle zebraInt() {
        return cache.computeIfAbsent("zebraInt", k -> {
            CellStyle style = numberInt();
            style = clone(style);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        });
    }

    /** Zebra-striping para porcentagem. */
    public CellStyle zebraPercent() {
        return cache.computeIfAbsent("zebraPercent", k -> {
            CellStyle style = numberPercent();
            style = clone(style);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        });
    }

    // ============================================================
    // Helpers
    // ============================================================

    private CellStyle clone(CellStyle src) {
        CellStyle newStyle = wb.createCellStyle();
        newStyle.cloneStyleFrom(src);
        return newStyle;
    }

    private void addBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}

