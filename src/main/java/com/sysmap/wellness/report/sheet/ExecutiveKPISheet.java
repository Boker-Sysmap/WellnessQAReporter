package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.style.ReportStyleManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;

import java.util.List;

/**
 * Gera a aba "Resumo Executivo" contendo KPIs e um gráfico de tendência.
 * Compatível com o fluxo multi-projeto do WellnessQAReporter.
 */
public class ExecutiveKPISheet {

    public static void create(XSSFWorkbook workbook, List<KPIData> kpis, String sheetName) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(workbook);

        // === Cabeçalho ===
        Row header = sheet.createRow(0);
        String[] headers = {"Indicador", "Valor", "Tendência", "Descrição"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }

        // === Estilos específicos (criados do zero, sem cloneStyleFrom) ===
        XSSFCellStyle percentStyle = workbook.createCellStyle();
        percentStyle.setAlignment(HorizontalAlignment.CENTER);
        percentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));

        XSSFCellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setAlignment(HorizontalAlignment.CENTER);
        numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

        // === Dados dos KPIs ===
        int rowNum = 1;
        for (KPIData kpi : kpis) {
            XSSFRow row = sheet.createRow(rowNum++);

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(kpi.getName());
            nameCell.setCellStyle(styles.get("left"));

            double value = kpi.getValue();
            Cell valueCell = row.createCell(1);

            if (value >= 0 && value <= 1) {
                valueCell.setCellValue(value);
                valueCell.setCellStyle(percentStyle);
            } else {
                valueCell.setCellValue(value);
                valueCell.setCellStyle(numberStyle);
            }

            Cell trendCell = row.createCell(2);
            trendCell.setCellValue(kpi.getTrendSymbol());
            trendCell.setCellStyle(styles.get("center"));

            Cell descCell = row.createCell(3);
            descCell.setCellValue(kpi.getDescription());
            descCell.setCellStyle(styles.get("left"));
        }

        // === Ajuste de colunas ===
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        // === Gráfico ===
        createTrendChart(sheet, kpis);
    }

    private static void createTrendChart(XSSFSheet sheet, List<KPIData> kpis) {
        if (kpis == null || kpis.isEmpty()) return;

        int chartStartRow = sheet.getLastRowNum() + 3;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, chartStartRow, 8, chartStartRow + 15);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Tendência de KPIs");
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("Indicadores");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Valor (%)");

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(1, kpis.size(), 0, 0));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(1, kpis.size(), 1, 1));

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        XDDFLineChartData.Series series = (XDDFLineChartData.Series) data.addSeries(categories, values);
        series.setTitle("Valor Atual", null);
        series.setSmooth(false);
        chart.plot(data);
    }
}
