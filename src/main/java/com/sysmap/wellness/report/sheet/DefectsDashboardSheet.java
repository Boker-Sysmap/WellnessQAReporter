package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aba "<PROJ> – Defeitos DashBoard" com KPIs, tendências, distribuições e gráficos.
 * Layout ajustado para evitar sobreposição de gráficos.
 */
public class DefectsDashboardSheet {

    private static class TrendBounds {
        final int headerRow, dataStart, dataEnd, nextRow;
        TrendBounds(int headerRow, int dataStart, int dataEnd, int nextRow) {
            this.headerRow = headerRow;
            this.dataStart = dataStart;
            this.dataEnd = dataEnd;
            this.nextRow = nextRow;
        }
    }

    private static class DistBounds {
        final int sevStartRow, sevRows, stsStartRow, stsRows, nextRow;
        DistBounds(int sevStartRow, int sevRows, int stsStartRow, int stsRows, int nextRow) {
            this.sevStartRow = sevStartRow;
            this.sevRows = sevRows;
            this.stsStartRow = stsStartRow;
            this.stsRows = stsRows;
            this.nextRow = nextRow;
        }
    }

    /** Cria a planilha de gráficos por projeto. */
    public static Sheet create(XSSFWorkbook wb, JSONObject defectsData, String sheetName) {
        XSSFSheet sheet = wb.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(wb);
        int rowIdx = 0;

        // === Cabeçalho ===
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Painel de Defeitos - Indicadores Executivos");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        // === KPIs ===
        Map<String, Double> kpis = calculateKpis(defectsData);
        rowIdx = createKpiSection(sheet, styles, kpis, rowIdx + 2);

        // === Tendência semanal (tabela) ===
        rowIdx += 1;
        Row trendHeader = sheet.createRow(rowIdx++);
        trendHeader.createCell(0).setCellValue("Tendência Semanal - Abertura x Fechamento de Defeitos");
        trendHeader.getCell(0).setCellStyle(styles.get("subtitle"));

        TrendBounds trend = createTrendSection(sheet, styles, defectsData, rowIdx);
        rowIdx = trend.nextRow + 2;

        // Gráfico de linhas abaixo da tabela de tendência
        if (trend.dataEnd >= trend.dataStart) {
            int chartTop = trend.dataEnd + 1;
            int chartBottom = chartTop + 15;
            createLineChartTrend(sheet, trend.headerRow, trend.dataStart, trend.dataEnd,
                    0, 2, 0, chartTop, 8, chartBottom, "Abertos x Fechados (Semanas)");
            rowIdx = chartBottom + 2;
        }

        // === Distribuições (tabelas) ===
        Row distHeader = sheet.createRow(rowIdx++);
        distHeader.createCell(0).setCellValue("Distribuição de Defeitos - Severidade, Status e Módulo");
        distHeader.getCell(0).setCellStyle(styles.get("subtitle"));
        DistBounds dist = createDistributionSection(sheet, styles, defectsData, rowIdx);
        rowIdx = dist.nextRow + 2;

        // Gráfico de colunas (Severidade) – posicionado logo após tabela de severidade
        if (dist.sevRows > 0) {
            int sevEndRow = dist.sevStartRow + dist.sevRows - 1;
            int chartTop = sevEndRow + 1;
            int chartBottom = chartTop + 15;
            createBarChart(sheet, 0, 1, dist.sevStartRow, sevEndRow,
                    0, chartTop, 6, chartBottom, "Defeitos por Severidade");
            rowIdx = Math.max(rowIdx, chartBottom + 2);
        }

        // Gráfico de pizza (Status) – posicionado a partir do rowIdx atual (para não sobrepor)
        if (dist.stsRows > 0) {
            int stsEndRow = dist.stsStartRow + dist.stsRows - 1;
            int chartTop = rowIdx;
            int chartBottom = chartTop + 15;
            createPieChart(sheet, 3, 4, dist.stsStartRow, stsEndRow,
                    7, chartTop, 13, chartBottom, "Distribuição por Status");
            rowIdx = chartBottom + 2;
        }

        // === Tabela de apoio ===
        createSupportTables(sheet, styles, defectsData, rowIdx);

        for (int i = 0; i < 12; i++) sheet.autoSizeColumn(i);

        LoggerUtils.info("✅ Aba '" + sheetName + "' criada com sucesso.");
        return sheet;
    }

    // =============== KPIs ===============
    private static Map<String, Double> calculateKpis(JSONObject defectsData) {
        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null || defects.isEmpty()) return Collections.emptyMap();

        long total = defects.length();
        long closed = 0;
        long reopened = 0;
        double totalResolutionDays = 0;
        long resolvedCount = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        for (Object obj : defects) {
            JSONObject defect = (JSONObject) obj;
            String status = defect.optString("status", "").toLowerCase(Locale.ROOT);
            if ("closed".equals(status)) closed++;
            if ("reopened".equals(status)) reopened++;

            String createdAt = defect.optString("created_at", null);
            String closedAt = defect.optString("closed_at", null);
            if (createdAt != null && closedAt != null) {
                try {
                    LocalDateTime created = LocalDateTime.parse(createdAt, formatter);
                    LocalDateTime closedTime = LocalDateTime.parse(closedAt, formatter);
                    double days = Duration.between(created, closedTime).toHours() / 24.0;
                    totalResolutionDays += days;
                    resolvedCount++;
                } catch (Exception ignored) {}
            }
        }

        double closureRate = total > 0 ? (closed * 100.0 / total) : 0;
        double reopenedRate = total > 0 ? (reopened * 100.0 / total) : 0;
        double avgResolutionDays = resolvedCount > 0 ? totalResolutionDays / resolvedCount : 0;

        Map<String, Double> kpis = new LinkedHashMap<>();
        kpis.put("Total de Defeitos", (double) total);
        kpis.put("Fechados", (double) closed);
        kpis.put("Taxa de Fechamento (%)", closureRate);
        kpis.put("Reabertos (%)", reopenedRate);
        kpis.put("Tempo Médio Resolução (dias)", avgResolutionDays);
        return kpis;
    }

    private static int createKpiSection(Sheet sheet, ReportStyleManager styles, Map<String, Double> kpis, int rowIdx) {
        Row headerRow = sheet.createRow(rowIdx++);
        Cell header = headerRow.createCell(0);
        header.setCellValue("Indicadores Principais");
        header.setCellStyle(styles.get("subtitle"));

        for (Map.Entry<String, Double> entry : kpis.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(Math.round(entry.getValue()));
            row.getCell(0).setCellStyle(styles.get("label"));
            row.getCell(1).setCellStyle(styles.get("value"));
        }
        return rowIdx;
    }

    // =============== Tendência semanal ===============
    private static TrendBounds createTrendSection(Sheet sheet, ReportStyleManager styles, JSONObject defectsData, int rowIdx) {
        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null || defects.isEmpty()) return new TrendBounds(rowIdx, rowIdx, rowIdx - 1, rowIdx);

        Map<String, Integer> openedPerWeek = new TreeMap<>();
        Map<String, Integer> closedPerWeek = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;
        WeekFields wf = WeekFields.of(Locale.getDefault());

        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            try {
                if (d.has("created_at")) {
                    LocalDate c = LocalDateTime.parse(d.getString("created_at"), fmt).toLocalDate();
                    openedPerWeek.merge(c.getYear() + "-W" + String.format("%02d", c.get(wf.weekOfWeekBasedYear())), 1, Integer::sum);
                }
                if (d.has("closed_at")) {
                    LocalDate c = LocalDateTime.parse(d.getString("closed_at"), fmt).toLocalDate();
                    closedPerWeek.merge(c.getYear() + "-W" + String.format("%02d", c.get(wf.weekOfWeekBasedYear())), 1, Integer::sum);
                }
            } catch (Exception ignored) {}
        }

        Row head = sheet.createRow(rowIdx++);
        head.createCell(0).setCellValue("Semana");
        head.createCell(1).setCellValue("Abertos");
        head.createCell(2).setCellValue("Fechados");
        for (int i = 0; i < 3; i++) head.getCell(i).setCellStyle(styles.get("header"));

        int start = rowIdx;
        Set<String> all = new TreeSet<>();
        all.addAll(openedPerWeek.keySet());
        all.addAll(closedPerWeek.keySet());

        for (String w : all) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(w);
            r.createCell(1).setCellValue(openedPerWeek.getOrDefault(w, 0));
            r.createCell(2).setCellValue(closedPerWeek.getOrDefault(w, 0));
            for (int i = 0; i < 3; i++) r.getCell(i).setCellStyle(styles.get("value"));
        }
        return new TrendBounds(start - 1, start, rowIdx - 1, rowIdx);
    }

    // =============== Distribuições ===============
    private static DistBounds createDistributionSection(Sheet sheet, ReportStyleManager styles, JSONObject defectsData, int rowIdx) {
        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null || defects.isEmpty()) return new DistBounds(rowIdx, 0, rowIdx, 0, rowIdx);

        Map<String, Integer> bySeverity = new TreeMap<>();
        Map<String, Integer> byStatus = new TreeMap<>();
        Map<String, Integer> byModule = new TreeMap<>();

        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            bySeverity.merge(d.optString("severity", "N/I"), 1, Integer::sum);
            byStatus.merge(d.optString("status", "Desconhecido"), 1, Integer::sum);
            byModule.merge(d.optString("component", "Sem módulo"), 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> topModules = byModule.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5).collect(Collectors.toList());

        Row head = sheet.createRow(rowIdx++);
        head.createCell(0).setCellValue("Severidade");
        head.createCell(1).setCellValue("Qtd");
        head.createCell(3).setCellValue("Status");
        head.createCell(4).setCellValue("Qtd");
        head.createCell(6).setCellValue("Top 5 Módulos");
        head.createCell(7).setCellValue("Qtd");
        for (int i : new int[]{0, 1, 3, 4, 6, 7}) head.getCell(i).setCellStyle(styles.get("header"));

        int sevStart = rowIdx;
        Iterator<Map.Entry<String, Integer>> itS = bySeverity.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> itT = byStatus.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> itM = topModules.iterator();

        int max = Math.max(Math.max(bySeverity.size(), byStatus.size()), topModules.size());
        for (int i = 0; i < max; i++) {
            Row r = sheet.createRow(rowIdx++);
            if (itS.hasNext()) { Map.Entry<String, Integer> e = itS.next(); r.createCell(0).setCellValue(e.getKey()); r.createCell(1).setCellValue(e.getValue()); }
            if (itT.hasNext()) { Map.Entry<String, Integer> e = itT.next(); r.createCell(3).setCellValue(e.getKey()); r.createCell(4).setCellValue(e.getValue()); }
            if (itM.hasNext()) { Map.Entry<String, Integer> e = itM.next(); r.createCell(6).setCellValue(e.getKey()); r.createCell(7).setCellValue(e.getValue()); }
            for (int c : new int[]{0, 1, 3, 4, 6, 7})
                if (r.getCell(c) != null) r.getCell(c).setCellStyle(styles.get("value"));
        }
        return new DistBounds(sevStart, bySeverity.size(), sevStart, byStatus.size(), rowIdx);
    }

    // =============== Tabela de apoio ===============
    private static void createSupportTables(Sheet sheet, ReportStyleManager styles, JSONObject defectsData, int rowIdx) {
        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null) return;

        Row h = sheet.createRow(rowIdx++);
        h.createCell(0).setCellValue("Top 10 Defeitos Abertos (mais antigos)");
        h.getCell(0).setCellStyle(styles.get("subtitle"));

        Row head = sheet.createRow(rowIdx++);
        String[] cols = {"Título", "Severidade", "Status", "Módulo", "Criado em"};
        for (int i = 0; i < cols.length; i++) {
            head.createCell(i).setCellValue(cols[i]);
            head.getCell(i).setCellStyle(styles.get("header"));
        }

        List<JSONObject> open = new ArrayList<>();
        for (Object o : defects) {
            JSONObject d = (JSONObject) o;
            if (!"closed".equalsIgnoreCase(d.optString("status"))) open.add(d);
        }

        open.sort(Comparator.comparing(d -> d.optString("created_at", "")));
        for (int i = 0; i < Math.min(10, open.size()); i++) {
            JSONObject d = open.get(i);
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(d.optString("title"));
            r.createCell(1).setCellValue(d.optString("severity"));
            r.createCell(2).setCellValue(d.optString("status"));
            r.createCell(3).setCellValue(d.optString("component"));
            r.createCell(4).setCellValue(d.optString("created_at"));
            for (int c = 0; c <= 4; c++) r.getCell(c).setCellStyle(styles.get("value"));
        }
    }

    // =============== Gráficos ===============
    private static void createLineChartTrend(XSSFSheet sheet,
                                             int headerRow, int dataStart, int dataEnd,
                                             int startCol, int endCol,
                                             int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2,
                                             String title) {
        if (dataEnd < dataStart) return;

        XSSFDrawing d = sheet.createDrawingPatriarch();
        XSSFClientAnchor a = d.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        a.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

        XSSFChart c = d.createChart(a);
        c.setTitleText(title);
        c.setTitleOverlay(false);

        XDDFCategoryAxis x = c.createCategoryAxis(AxisPosition.BOTTOM);
        x.setTitle("Semana");
        XDDFValueAxis y = c.createValueAxis(AxisPosition.LEFT);
        y.setTitle("Quantidade");

        XDDFDataSource<String> cats =
                XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(dataStart, dataEnd, startCol, startCol));
        XDDFNumericalDataSource<Double> s1 =
                XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(dataStart, dataEnd, startCol + 1, startCol + 1));
        XDDFNumericalDataSource<Double> s2 =
                XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(dataStart, dataEnd, startCol + 2, startCol + 2));

        XDDFLineChartData data = (XDDFLineChartData) c.createData(ChartTypes.LINE, x, y);
        data.addSeries(cats, s1).setTitle("Abertos", null);
        data.addSeries(cats, s2).setTitle("Fechados", null);
        c.plot(data);
    }

    private static void createBarChart(XSSFSheet sheet,
                                       int catCol, int valCol,
                                       int startRow, int endRow,
                                       int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2,
                                       String title) {
        if (endRow < startRow) return;

        XSSFDrawing d = sheet.createDrawingPatriarch();
        XSSFClientAnchor a = d.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        a.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

        XSSFChart c = d.createChart(a);
        c.setTitleText(title);
        c.setTitleOverlay(false);

        XDDFCategoryAxis x = c.createCategoryAxis(AxisPosition.BOTTOM);
        x.setTitle("Categoria");
        XDDFValueAxis y = c.createValueAxis(AxisPosition.LEFT);
        y.setTitle("Quantidade");

        XDDFDataSource<String> cats =
                XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(startRow, endRow, catCol, catCol));
        XDDFNumericalDataSource<Double> vals =
                XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(startRow, endRow, valCol, valCol));

        XDDFChartData data = c.createData(ChartTypes.BAR, x, y);
        XDDFChartData.Series series = data.addSeries(cats, vals);
        series.setTitle("Quantidade", null);
        c.plot(data);

        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.COL);
        bar.setBarGrouping(BarGrouping.CLUSTERED);
    }

    private static void createPieChart(XSSFSheet sheet,
                                       int catCol, int valCol,
                                       int startRow, int endRow,
                                       int anchorCol1, int anchorRow1, int anchorCol2, int anchorRow2,
                                       String title) {
        if (endRow < startRow) return;

        XSSFDrawing d = sheet.createDrawingPatriarch();
        XSSFClientAnchor a = d.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        a.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

        XSSFChart c = d.createChart(a);
        c.setTitleText(title);
        c.setTitleOverlay(false);

        XDDFDataSource<String> cats =
                XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(startRow, endRow, catCol, catCol));
        XDDFNumericalDataSource<Double> vals =
                XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(startRow, endRow, valCol, valCol));

        XDDFChartData data = c.createData(ChartTypes.PIE, null, null);
        data.addSeries(cats, vals);
        c.plot(data);
    }
}
