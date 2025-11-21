package com.sysmap.wellness.core.excel.builder;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.core.excel.sheet.DefectAnalyticalReportSheet;
import com.sysmap.wellness.core.excel.sheet.DefectsDashboardSheet;
import com.sysmap.wellness.core.excel.sheet.DefectsSyntheticSheet;
import com.sysmap.wellness.core.excel.sheet.ExecutiveConsolidatedSheet;
import com.sysmap.wellness.core.excel.sheet.ExecutiveKPISheet;
import com.sysmap.wellness.core.excel.sheet.FunctionalSummarySheet;
import com.sysmap.wellness.utils.LoggerUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Constrói todas as abas do relatório Excel.
 */
public class ReportWorkbookBuilder {

    public XSSFWorkbook buildWorkbook(
        Map<String, JSONObject> consolidatedData,
        Map<String, List<KPIData>> kpisByProject,
        Map<String, Set<String>> releasesByProject,
        Map<String, JSONObject> functionalSummaries,
        Map<String, JSONArray> enrichedDefects
    ) {
        LoggerUtils.section("📗 Montando workbook Excel (abas, estilos, colunas)");

        XSSFWorkbook wb = new XSSFWorkbook();

        // ======================================================
        // 1) Painel Consolidado  (NOVO FORMATO)
        // ======================================================
        ExecutiveConsolidatedSheet.create(
            wb,
            consolidatedData   // agora passa o consolidatedData inteiro
        );
        wb.setSheetOrder("Painel Consolidado", 0);

        // ======================================================
        // 2) Resumo Executivo por Projeto (mantém KPIData)
        // ======================================================
        for (String project : kpisByProject.keySet()) {

            Set<String> projectReleases = releasesByProject.get(project);
            if (projectReleases == null || projectReleases.isEmpty()) continue;

            // Apenas a release mais recente
            String latestRelease = projectReleases.iterator().next();

            ExecutiveKPISheet.create(
                wb,
                kpisByProject.get(project),
                project + " – Resumo Executivo",
                latestRelease
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

            JSONObject d = new JSONObject().put("defects", enrichedDefects.get(project));

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

            JSONObject d = new JSONObject().put("defects", enrichedDefects.get(project));

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
