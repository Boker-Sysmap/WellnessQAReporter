package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

/**
 * Resumo Executivo por projeto.
 * Por enquanto exibe apenas a release atual e os KPIs calculados no run.
 */
public class ExecutiveKPISheet {

    public static void create(
        XSSFWorkbook wb,
        List<KPIData> kpis,
        String sheetName,
        String currentReleaseId
    ) {
        Sheet sheet = wb.createSheet(sheetName);

        CellStyle numberStyle = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        numberStyle.setDataFormat(format.getFormat("0.00"));

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Release");
        header.createCell(1).setCellValue("KPI");
        header.createCell(2).setCellValue("Valor");

        int rowIndex = 1;

        for (KPIData kpi : kpis) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(currentReleaseId);
            row.createCell(1).setCellValue(kpi.getName());
            Cell valCell = row.createCell(2);
            valCell.setCellValue(kpi.getValue());
            valCell.setCellStyle(numberStyle);
        }
    }
}
