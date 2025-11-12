package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;

import java.util.*;

/**
 * Painel Consolidado de KPIs entre projetos (Fully, Chubb, etc.)
 * Corrigido com formatação percentual automática, espaçamento e gráfico escalado.
 */
public class ExecutiveConsolidatedSheet {

    public static void create(XSSFWorkbook workbook, Map<String, List<KPIData>> kpisByProject) {
        String sheetName = "Painel Consolidado";
        XSSFSheet sheet = workbook.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(workbook);
        LoggerUtils.step("📈 Criando aba: " + sheetName);

        // === Título Principal ===
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Painel Consolidado de KPIs – Visão Executiva");
        XSSFCellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, kpisByProject.size()));

        // === Cabeçalho ===
        Row header = sheet.createRow(2);
        header.createCell(0).setCellValue("Indicador");
        int col = 1;
        for (String project : kpisByProject.keySet()) {
            Cell c = header.createCell(col++);
            c.setCellValue(project);
        }
        for (int i = 0; i < col; i++) {
            header.getCell(i).setCellStyle(styles.get("header"));
        }

        // === Lista de todos os indicadores ===
        Set<String> indicadores = new LinkedHashSet<>();
        for (List<KPIData> list : kpisByProject.values()) {
            for (KPIData k : list) indicadores.add(k.getName());
        }

        // === Dados ===
        int rowNum = 3;
        for (String indicador : indicadores) {
            Row row = sheet.createRow(rowNum++);
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(indicador);
            nameCell.setCellStyle(styles.get("left"));

            col = 1;
            for (String project : kpisByProject.keySet()) {
                List<KPIData> list = kpisByProject.get(project);
                Optional<KPIData> kpiOpt = list.stream()
                        .filter(k -> k.getName().equals(indicador))
                        .findFirst();

                Cell valCell = row.createCell(col++);
                if (kpiOpt.isEmpty()) {
                    valCell.setCellValue("-");
                    valCell.setCellStyle(styles.get("center"));
                    continue;
                }

                KPIData kpi = kpiOpt.get();
                double value = kpi.getValue();

                // === Detecção automática de formato ===
                XSSFCellStyle style = workbook.createCellStyle();
                style.setAlignment(HorizontalAlignment.CENTER);
                style.setVerticalAlignment(VerticalAlignment.CENTER);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);

                DataFormat df = workbook.createDataFormat();
                if (value >= 0 && value <= 1) {
                    style.setDataFormat(df.getFormat("0.00%"));
                    valCell.setCellValue(value);
                } else if (value > 1 && value <= 100) {
                    style.setDataFormat(df.getFormat("0.00%"));
                    valCell.setCellValue(value / 100); // corrige percentuais 0–100
                } else {
                    style.setDataFormat(df.getFormat("#,##0.00"));
                    valCell.setCellValue(value);
                }

                valCell.setCellStyle(style);
            }
        }

        // === Ajuste de colunas (com padding extra) ===
        for (int i = 0; i < kpisByProject.size() + 1; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(width + 1200, 14000));
        }

        // === Gráfico comparativo ===
        createComparisonChart(sheet, kpisByProject, indicadores);

        LoggerUtils.success("✅ Painel Consolidado criado com sucesso!");
    }

    private static void createComparisonChart(XSSFSheet sheet,
                                              Map<String, List<KPIData>> kpisByProject,
                                              Set<String> indicadores) {
        if (kpisByProject.isEmpty() || indicadores.isEmpty()) return;

        int chartStartRow = sheet.getLastRowNum() + 4;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                0, chartStartRow, 9, chartStartRow + 18);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Comparativo de Indicadores por Projeto");
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("Indicadores");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Valor (%)");
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        // Limitar eixo Y a 100%
        leftAxis.setMaximum(1.0);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(3, 3 + indicadores.size() - 1, 0, 0)
        );

        XDDFBarChartData data = (XDDFBarChartData)
                chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        data.setBarDirection(BarDirection.COL);

        int projectCol = 1;
        for (String project : kpisByProject.keySet()) {
            XDDFNumericalDataSource<Double> values =
                    XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                            new CellRangeAddress(3, 3 + indicadores.size() - 1, projectCol, projectCol));
            XDDFBarChartData.Series series = (XDDFBarChartData.Series)
                    data.addSeries(categories, values);
            series.setTitle(project, null);
            projectCol++;
        }

        chart.plot(data);
    }
}
