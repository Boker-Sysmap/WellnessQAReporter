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
 * Responsável pela geração da aba
 * <b>"&lt;PROJ&gt; – Defeitos Dashboard"</b> do relatório Excel.
 *
 * <p>Este dashboard apresenta uma visão executiva da saúde do processo
 * de defeitos de um determinado projeto, fornecendo:</p>
 *
 * <ul>
 *   <li><b>KPIs principais</b> (taxa de fechamento, reabertura, tempo médio, total);</li>
 *   <li><b>Tendência semanal</b> de abertura e fechamento de defeitos;</li>
 *   <li><b>Distribuições</b> por severidade, status e módulos;</li>
 *   <li><b>Gráficos</b> dinâmicos de linhas, colunas e pizza;</li>
 *   <li><b>Top 10 defeitos abertos mais antigos</b>;</li>
 * </ul>
 *
 * <p>A classe gera não apenas tabelas, mas também os gráficos
 * correspondentes posicionados automaticamente sem sobreposição.</p>
 *
 * <p>Ela é completamente independente e pode ser reutilizada para múltiplos
 * projetos dentro do mesmo workbook.</p>
 */
public class DefectsDashboardSheet {

    /**
     * Armazena os limites da tabela de tendência semanal.
     * Usado para calcular a área do gráfico sem sobreposição.
     */
    private static class TrendBounds {
        final int headerRow;
        final int dataStart;
        final int dataEnd;
        final int nextRow;

        TrendBounds(int headerRow, int dataStart, int dataEnd, int nextRow) {
            this.headerRow = headerRow;
            this.dataStart = dataStart;
            this.dataEnd = dataEnd;
            this.nextRow = nextRow;
        }
    }

    /**
     * Agrupa os limites das tabelas de distribuição (severidade, status, módulo).
     * Usado para posicionamento automático dos gráficos subsequentes.
     */
    private static class DistBounds {
        final int sevStartRow;
        final int sevRows;
        final int stsStartRow;
        final int stsRows;
        final int nextRow;

        DistBounds(int sevStartRow, int sevRows, int stsStartRow, int stsRows, int nextRow) {
            this.sevStartRow = sevStartRow;
            this.sevRows = sevRows;
            this.stsStartRow = stsStartRow;
            this.stsRows = stsRows;
            this.nextRow = nextRow;
        }
    }

    /**
     * Cria a aba completa do dashboard de defeitos para um projeto.
     *
     * @param wb        Workbook de destino.
     * @param defectsData JSONObject contendo os dados normalizados de defeitos
     *                    preparados pelo serviço {@link com.sysmap.wellness.report.service.DefectAnalyticalService}.
     * @param sheetName Nome da aba no Excel (ex.: "Fully – Defeitos Dashboard").
     * @return A referência da planilha criada.
     */
    public static Sheet create(XSSFWorkbook wb, JSONObject defectsData, String sheetName) {
        XSSFSheet sheet = wb.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(wb);
        int rowIdx = 0;

        // === Cabeçalho principal ===
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Painel de Defeitos - Indicadores Executivos");
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        // === KPIs ===
        Map<String, Double> kpis = calculateKpis(defectsData);
        rowIdx = createKpiSection(sheet, styles, kpis, rowIdx + 2);

        // === Tendência semanal ===
        rowIdx += 1;
        Row trendHeader = sheet.createRow(rowIdx++);
        trendHeader.createCell(0).setCellValue("Tendência Semanal - Abertura x Fechamento de Defeitos");
        trendHeader.getCell(0).setCellStyle(styles.get("subtitle"));

        TrendBounds trend = createTrendSection(sheet, styles, defectsData, rowIdx);
        rowIdx = trend.nextRow + 2;

        // === Gráfico de linha da tendência ===
        if (trend.dataEnd >= trend.dataStart) {
            int chartTop = trend.dataEnd + 1;
            int chartBottom = chartTop + 15;

            createLineChartTrend(
                sheet,
                trend.headerRow,
                trend.dataStart,
                trend.dataEnd,
                0, 2,
                0, chartTop,
                8, chartBottom,
                "Abertos x Fechados (Semanas)"
            );

            rowIdx = chartBottom + 2;
        }

        // === Distribuições ===
        Row distHeader = sheet.createRow(rowIdx++);
        distHeader.createCell(0).setCellValue("Distribuição de Defeitos - Severidade, Status e Módulo");
        distHeader.getCell(0).setCellStyle(styles.get("subtitle"));

        DistBounds dist = createDistributionSection(sheet, styles, defectsData, rowIdx);
        rowIdx = dist.nextRow + 2;

        // === Gráfico de colunas (Severidade) ===
        if (dist.sevRows > 0) {
            int sevEndRow = dist.sevStartRow + dist.sevRows - 1;
            int chartTop = sevEndRow + 1;
            int chartBottom = chartTop + 15;

            createBarChart(sheet, 0, 1, dist.sevStartRow, sevEndRow,
                0, chartTop, 6, chartBottom, "Defeitos por Severidade");

            rowIdx = Math.max(rowIdx, chartBottom + 2);
        }

        // === Gráfico de pizza (Status) ===
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

        // Autosize das colunas principais
        for (int i = 0; i < 12; i++) sheet.autoSizeColumn(i);

        LoggerUtils.info("✅ Aba '" + sheetName + "' criada com sucesso.");
        return sheet;
    }

    // =========================================================
    // SECTION: KPIs principais
    // =========================================================

    /**
     * Calcula os principais KPIs de defeitos:
     * <ul>
     *   <li>Total de defeitos</li>
     *   <li>Qtd. fechados</li>
     *   <li>Taxa de fechamento (%)</li>
     *   <li>Reabertos (%)</li>
     *   <li>Tempo médio de resolução (dias)</li>
     * </ul>
     *
     * @param defectsData JSON contendo a lista "defects".
     * @return Mapa ordenado chave → valor com todos os KPIs calculados.
     */
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

            // Tempo médio de resolução
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

    /**
     * Renderiza no Excel a seção de KPIs principais.
     *
     * @param sheet  Sheet alvo.
     * @param styles Gerenciador de estilos.
     * @param kpis   Mapa de indicadores.
     * @param rowIdx Linha inicial.
     * @return A próxima linha livre após a tabela.
     */
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

    // =========================================================
    // SECTION: Tendência semanal
    // =========================================================

    /**
     * Gera a tabela de tendência semanal, agrupando defeitos por semana de abertura e fechamento.
     *
     * @param sheet       Aba do Excel.
     * @param styles      Estilos pré-definidos.
     * @param defectsData Dados de defeitos.
     * @param rowIdx      Linha inicial.
     * @return Estrutura {@link TrendBounds} contendo limites úteis para gráficos.
     */
    private static TrendBounds createTrendSection(Sheet sheet, ReportStyleManager styles, JSONObject defectsData, int rowIdx) {
        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null || defects.isEmpty()) {
            return new TrendBounds(rowIdx, rowIdx, rowIdx - 1, rowIdx);
        }

        Map<String, Integer> openedPerWeek = new TreeMap<>();
        Map<String, Integer> closedPerWeek = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;
        WeekFields wf = WeekFields.of(Locale.getDefault());

        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            try {
                if (d.has("created_at")) {
                    LocalDate c = LocalDateTime.parse(d.getString("created_at"), fmt).toLocalDate();
                    openedPerWeek.merge(
                        c.getYear() + "-W" + String.format("%02d", c.get(wf.weekOfWeekBasedYear())),
                        1,
                        Integer::sum
                    );
                }
                if (d.has("closed_at")) {
                    LocalDate c = LocalDateTime.parse(d.getString("closed_at"), fmt).toLocalDate();
                    closedPerWeek.merge(
                        c.getYear() + "-W" + String.format("%02d", c.get(wf.weekOfWeekBasedYear())),
                        1,
                        Integer::sum
                    );
                }
            } catch (Exception ignored) {}
        }

        // Cabeçalho
        Row head = sheet.createRow(rowIdx++);
        head.createCell(0).setCellValue("Semana");
        head.createCell(1).setCellValue("Abertos");
        head.createCell(2).setCellValue("Fechados");
        for (int i = 0; i < 3; i++) head.getCell(i).setCellStyle(styles.get("header"));

        int start = rowIdx;

        // Unifica semanas
        Set<String> allWeeks = new TreeSet<>();
        allWeeks.addAll(openedPerWeek.keySet());
        allWeeks.addAll(closedPerWeek.keySet());

        // Cria linhas
        for (String w : allWeeks) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(w);
            r.createCell(1).setCellValue(openedPerWeek.getOrDefault(w, 0));
            r.createCell(2).setCellValue(closedPerWeek.getOrDefault(w, 0));
            for (int i = 0; i < 3; i++) r.getCell(i).setCellStyle(styles.get("value"));
        }

        return new TrendBounds(start - 1, start, rowIdx - 1, rowIdx);
    }

    // =========================================================
    // SECTION: Distribuições
    // =========================================================

    /**
     * Gera tabelas de distribuição por severidade, status e módulo.
     *
     * @param sheet       Aba.
     * @param styles      Estilos.
     * @param defectsData Dados dos defeitos.
     * @param rowIdx      Linha inicial.
     * @return {@link DistBounds} com limites das seções.
     */
    private static DistBounds createDistributionSection(Sheet sheet, ReportStyleManager styles, JSONObject defectsData, int rowIdx) {
        JSONArray defects = defectsData.optJSONArray("defects");
        if (defects == null || defects.isEmpty()) {
            return new DistBounds(rowIdx, 0, rowIdx, 0, rowIdx);
        }

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
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        Row head = sheet.createRow(rowIdx++);
        head.createCell(0).setCellValue("Severidade");
        head.createCell(1).setCellValue("Qtd");
        head.createCell(3).setCellValue("Status");
        head.createCell(4).setCellValue("Qtd");
        head.createCell(6).setCellValue("Top 5 Módulos");
        head.createCell(7).setCellValue("Qtd");

        for (int col : new int[]{0, 1, 3, 4, 6, 7})
            head.getCell(col).setCellStyle(styles.get("header"));

        int sevStart = rowIdx;

        Iterator<Map.Entry<String, Integer>> itSev = bySeverity.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> itSts = byStatus.entrySet().iterator();
        Iterator<Map.Entry<String, Integer>> itMod = topModules.iterator();

        int max = Math.max(Math.max(bySeverity.size(), byStatus.size()), topModules.size());

        for (int i = 0; i < max; i++) {
            Row r = sheet.createRow(rowIdx++);

            if (itSev.hasNext()) {
                var e = itSev.next();
                r.createCell(0).setCellValue(e.getKey());
                r.createCell(1).setCellValue(e.getValue());
            }

            if (itSts.hasNext()) {
                var e = itSts.next();
                r.createCell(3).setCellValue(e.getKey());
                r.createCell(4).setCellValue(e.getValue());
            }

            if (itMod.hasNext()) {
                var e = itMod.next();
                r.createCell(6).setCellValue(e.getKey());
                r.createCell(7).setCellValue(e.getValue());
            }

            for (int col : new int[]{0, 1, 3, 4, 6, 7})
                if (r.getCell(col) != null)
                    r.getCell(col).setCellStyle(styles.get("value"));
        }

        return new DistBounds(sevStart, bySeverity.size(), sevStart, byStatus.size(), rowIdx);
    }

    // =========================================================
    // SECTION: Tabela de apoio
    // =========================================================

    /**
     * Cria a tabela “Top 10 defeitos abertos mais antigos”.
     *
     * @param sheet       Aba destino.
     * @param styles      Estilos.
     * @param defectsData JSON contendo a lista de defeitos.
     * @param rowIdx      Linha inicial.
     */
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
        for (Object obj : defects) {
            JSONObject d = (JSONObject) obj;
            if (!"closed".equalsIgnoreCase(d.optString("status")))
                open.add(d);
        }

        // Ordena do mais antigo para o mais recente
        open.sort(Comparator.comparing(d -> d.optString("created_at", "")));

        for (int i = 0; i < Math.min(10, open.size()); i++) {
            JSONObject d = open.get(i);
            Row r = sheet.createRow(rowIdx++);

            r.createCell(0).setCellValue(d.optString("title"));
            r.createCell(1).setCellValue(d.optString("severity"));
            r.createCell(2).setCellValue(d.optString("status"));
            r.createCell(3).setCellValue(d.optString("component"));
            r.createCell(4).setCellValue(d.optString("created_at"));

            for (int c = 0; c <= 4; c++)
                r.getCell(c).setCellStyle(styles.get("value"));
        }
    }

    // =========================================================
    // SECTION: Gráficos
    // =========================================================

    /**
     * Cria um gráfico de linhas representando a tendência semanal
     * de abertos x fechados.
     *
     * @param sheet      Planilha.
     * @param headerRow  Índice do cabeçalho.
     * @param dataStart  Primeira linha de dados.
     * @param dataEnd    Última linha de dados.
     * @param startCol   Coluna inicial dos dados.
     * @param endCol     Coluna final dos dados.
     * @param anchorCol1 Coluna inicial do gráfico.
     * @param anchorRow1 Linha inicial do gráfico.
     * @param anchorCol2 Coluna final do gráfico.
     * @param anchorRow2 Linha final do gráfico.
     * @param title      Título do gráfico.
     */
    private static void createLineChartTrend(
        XSSFSheet sheet,
        int headerRow,
        int dataStart,
        int dataEnd,
        int startCol,
        int endCol,
        int anchorCol1,
        int anchorRow1,
        int anchorCol2,
        int anchorRow2,
        String title
    ) {
        if (dataEnd < dataStart) return;

        XSSFDrawing d = sheet.createDrawingPatriarch();
        XSSFClientAnchor a = d.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);
        a.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);

        XSSFChart chart = d.createChart(a);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        xAxis.setTitle("Semana");

        XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
        yAxis.setTitle("Quantidade");

        XDDFDataSource<String> categories =
            XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(dataStart, dataEnd, startCol, startCol));

        XDDFNumericalDataSource<Double> seriesOpened =
            XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(dataStart, dataEnd, startCol + 1, startCol + 1));

        XDDFNumericalDataSource<Double> seriesClosed =
            XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(dataStart, dataEnd, startCol + 2, startCol + 2));

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, xAxis, yAxis);

        data.addSeries(categories, seriesOpened).setTitle("Abertos", null);
        data.addSeries(categories, seriesClosed).setTitle("Fechados", null);

        chart.plot(data);
    }

    /**
     * Cria um gráfico de barras representando a distribuição por severidade.
     */
    private static void createBarChart(
        XSSFSheet sheet,
        int catCol,
        int valCol,
        int startRow,
        int endRow,
        int anchorCol1,
        int anchorRow1,
        int anchorCol2,
        int anchorRow2,
        String title
    ) {
        if (endRow < startRow) return;

        XSSFDrawing draw = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor =
            draw.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);

        XSSFChart chart = draw.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        xAxis.setTitle("Categoria");

        XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
        yAxis.setTitle("Quantidade");

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, new CellRangeAddress(startRow, endRow, catCol, catCol));

        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, new CellRangeAddress(startRow, endRow, valCol, valCol));

        XDDFChartData data = chart.createData(ChartTypes.BAR, xAxis, yAxis);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle("Quantidade", null);

        chart.plot(data);

        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.COL);
        bar.setBarGrouping(BarGrouping.CLUSTERED);
    }

    /**
     * Cria um gráfico de pizza para distribuição de status.
     */
    private static void createPieChart(
        XSSFSheet sheet,
        int catCol,
        int valCol,
        int startRow,
        int endRow,
        int anchorCol1,
        int anchorRow1,
        int anchorCol2,
        int anchorRow2,
        String title
    ) {
        if (endRow < startRow) return;

        XSSFDrawing draw = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor =
            draw.createAnchor(0, 0, 0, 0, anchorCol1, anchorRow1, anchorCol2, anchorRow2);

        XSSFChart chart = draw.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, new CellRangeAddress(startRow, endRow, catCol, catCol));

        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, new CellRangeAddress(startRow, endRow, valCol, valCol));

        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        data.addSeries(categories, values);
        chart.plot(data);
    }
}
