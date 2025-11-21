package com.sysmap.wellness.core.excel.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aba de Resumo Executivo – exibe os KPIs principais da release atual.
 *
 * Após a atualização do KPIEngine, esta aba agora suporta todos os KPIs:
 * - Escopo planejado
 * - Cobertura da release
 * - Resultados da release (% Passed / Failed / Blocked / Skipped / Retest)
 */
public class ExecutiveKPISheet {

    /**
     * Labels amigáveis para exibição
     * (ordem mantida conforme entrada da lista de KPIs).
     */
    private static final Map<String, String> KPI_LABELS = new LinkedHashMap<>() {{
        put("plannedScope", "Escopo planejado");
        put("releaseCoverage", "Cobertura da Release (%)");

        put("releaseResults.passedPct", "Resultado - Passed (%)");
        put("releaseResults.failedPct", "Resultado - Failed (%)");
        put("releaseResults.blockedPct", "Resultado - Blocked (%)");
        put("releaseResults.skippedPct", "Resultado - Skipped (%)");
        put("releaseResults.retestPct", "Resultado - Retest (%)");
    }};

    /**
     * Cria a aba de Resumo Executivo do projeto.
     */
    public static void create(
        XSSFWorkbook wb,
        List<KPIData> kpis,
        String sheetName,
        String currentReleaseId
    ) {
        Sheet sheet = wb.createSheet(sheetName);

        // Estilos
        CellStyle integerStyle = wb.createCellStyle();
        CellStyle percentTextStyle = wb.createCellStyle();
        CellStyle decimalStyle = wb.createCellStyle();

        DataFormat df = wb.createDataFormat();
        integerStyle.setDataFormat(df.getFormat("0"));
        percentTextStyle.setDataFormat(df.getFormat("@"));
        decimalStyle.setDataFormat(df.getFormat("0.00"));

        // Cabeçalho
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Release");
        header.createCell(1).setCellValue("KPI");
        header.createCell(2).setCellValue("Valor");

        int rowIndex = 1;

        for (KPIData kpi : kpis) {

            String key = kpi.getKey();

            // Nome para exibição
            String label = KPI_LABELS.getOrDefault(key, kpi.getName());

            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(currentReleaseId);
            row.createCell(1).setCellValue(label);

            Cell valCell = row.createCell(2);

            // ============================
            // REGRAS DE FORMATAÇÃO
            // ============================
            if ("plannedScope".equals(key)) {

                valCell.setCellValue(kpi.getValue());
                valCell.setCellStyle(integerStyle);

            } else if ("releaseCoverage".equals(key)) {

                valCell.setCellValue(kpi.getFormattedValue());
                valCell.setCellStyle(percentTextStyle);

            } else if (key.startsWith("releaseResults.")) {

                // KPIs de resultados (%)
                valCell.setCellValue(kpi.getFormattedValue());
                valCell.setCellStyle(percentTextStyle);

            } else {

                // KPIs diversos (fallback)
                valCell.setCellValue(kpi.getFormattedValue());
                valCell.setCellStyle(decimalStyle);
            }
        }
    }
}
