package com.sysmap.wellness.report.sheet;

import org.apache.poi.ss.usermodel.*;

public class ExcelStyleFactory {
    public static CellStyle createStyle(Workbook wb, boolean bold, boolean grayBackground, HorizontalAlignment align) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        if (grayBackground) {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        if (bold) {
            Font font = wb.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        return style;
    }
}
