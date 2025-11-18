package com.sysmap.wellness.report.generator;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.sheet.DefectAnalyticalReportSheet;
import com.sysmap.wellness.report.sheet.DefectsDashboardSheet;
import com.sysmap.wellness.report.sheet.DefectsSyntheticSheet;
import com.sysmap.wellness.report.sheet.ExecutiveConsolidatedSheet;
import com.sysmap.wellness.report.sheet.ExecutiveKPISheet;
import com.sysmap.wellness.report.sheet.FunctionalSummarySheet;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Responsável por montar o {@link XSSFWorkbook} completo,
 * incluindo todas as abas:
 *
 * <ul>
 *   <li>Painel Consolidado;</li>
 *   <li>Resumo Executivo por Projeto;</li>
 *   <li>Resumo Funcional;</li>
 *   <li>Defeitos Analítico;</li>
 *   <li>Defeitos Dashboard;</li>
 *   <li>Defeitos Sintético.</li>
 * </ul>
 *
 * <p>Também aplica auto-ajuste de colunas após a criação
 * de todas as abas.</p>
 */
public class ReportWorkbookBuilder {

    /**
     * Constrói o workbook Excel completo.
     *
     * @param consolidatedData   Dados consolidados por projeto (não são alterados).
     * @param kpisByProject      KPIs por projeto.
     * @param releaseByProject   Release principal por projeto.
     * @param functionalSummaries Resumo funcional por projeto.
     * @param enrichedDefects    Defeitos enriquecidos por projeto.
     * @return Workbook Excel já montado e com colunas ajustadas.
     */
    public XSSFWorkbook buildWorkbook(
        Map<String, JSONObject> consolidatedData,
        Map<String, List<KPIData>> kpisByProject,
        Map<String, String> releaseByProject,
        Map<String, JSONObject> functionalSummaries,
        Map<String, JSONArray> enrichedDefects
    ) {
        LoggerUtils.section("📗 Montando workbook Excel (abas, estilos, colunas)");

        XSSFWorkbook wb = new XSSFWorkbook();

        // ======================================================
        // 1) Painel Consolidado
        // ======================================================
        ExecutiveConsolidatedSheet.create(
            wb,
            kpisByProject,
            releaseByProject
        );
        wb.setSheetOrder("Painel Consolidado", 0);

        // ======================================================
        // 2) Resumo Executivo por Projeto
        // ======================================================
        for (String project : kpisByProject.keySet()) {

            String releaseId = releaseByProject.get(project);

            ExecutiveKPISheet.create(
                wb,
                kpisByProject.get(project),
                project + " – Resumo Executivo",
                releaseId
            );
        }

        // ======================================================
        // 3) Resumo Funcional
        // ======================================================
        for (String project : functionalSummaries.keySet()) {

            JSONObject summary = functionalSummaries.get(project);
            Map<String, JSONObject> map = new LinkedHashMap<>();
            map.put(project, summary);

            new FunctionalSummarySheet().create(
                wb,
                map,
                project + " – Resumo Funcional"
            );
        }

        // ======================================================
        // 4) Defeitos Analítico
        // ======================================================
        for (String project : enrichedDefects.keySet()) {

            Map<String, JSONArray> map = new LinkedHashMap<>();
            map.put(project, enrichedDefects.get(project));

            new DefectAnalyticalReportSheet().create(
                wb,
                map,
                project + " – Defeitos Analítico"
            );
        }

        // ======================================================
        // 5) Dashboard de Defeitos
        // ======================================================
        for (String project : enrichedDefects.keySet()) {

            JSONObject d = new JSONObject();
            d.put("defects", enrichedDefects.get(project));

            DefectsDashboardSheet.create(
                wb,
                d,
                project + " – Defeitos Dashboard"
            );
        }

        // ======================================================
        // 6) Defeitos Sintético
        // ======================================================
        for (String project : enrichedDefects.keySet()) {

            JSONObject d = new JSONObject();
            d.put("defects", enrichedDefects.get(project));

            DefectsSyntheticSheet.create(
                wb,
                d,
                project + " – Defeitos Sintético"
            );
        }

        // ======================================================
        // 7) Ajuste de colunas
        // ======================================================
        adjustAllColumns(wb);

        LoggerUtils.success("📗 Workbook Excel montado com sucesso.");
        return wb;
    }

    /**
     * Ajusta automaticamente a largura das colunas de todas as abas.
     *
     * @param wb Workbook Excel criado.
     */
    private void adjustAllColumns(Workbook wb) {

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {

            Sheet sheet = wb.getSheetAt(i);
            if (sheet.getRow(0) == null) continue;

            int cols = sheet.getRow(0).getPhysicalNumberOfCells();

            for (int c = 0; c < cols; c++) {
                try {
                    sheet.autoSizeColumn(c);
                    sheet.setColumnWidth(
                        c,
                        Math.min(sheet.getColumnWidth(c) + 1500, 18000)
                    );
                } catch (Exception ignored) {}
            }
        }
    }
}
