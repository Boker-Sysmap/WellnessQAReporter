package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.datetime.BusinessTimeCalculator;
import com.sysmap.wellness.utils.datetime.WorkSchedule;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gera a planilha "Gestão de Defeitos - Analítico" com formatação padronizada.
 *
 * Essa versão utiliza {@link ReportStyleManager} para aplicar estilos consistentes
 * (bordas, alinhamento e fontes) em todas as células, garantindo o mesmo padrão
 * visual dos demais relatórios (ex: Resumo por Funcionalidade).
 *
 * Colunas:
 * Projeto | Funcionalidade | Título | Ticket | Status | Severidade |
 * Criado em | Reportado por | Reportado em | Tempo em aberto | Resolvido em | Tempo de Resolução
 *
 * Regras:
 * - Insere linha em branco entre projetos.
 * - Ordena por projeto e em seguida por funcionalidade (suite.title).
 * - Formata datas no padrão brasileiro (dd/MM/yyyy HH:mm).
 * - Calcula o tempo de resolução (HH:mm) apenas quando {@code resolved_at} estiver presente,
 *   usando horas úteis (WorkSchedule + BusinessTimeCalculator).
 * - Calcula o tempo em aberto (HH:mm) apenas quando {@code resolved_at} estiver ausente,
 *   com base no tempo útil entre a data de reporte e o horário atual.
 */
public class DefectAnalyticalReportSheet {

    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Singletons para evitar recarregar feriados/config toda vez
    private static final WorkSchedule workSchedule = new WorkSchedule();
    private static final BusinessTimeCalculator businessTimeCalculator = new BusinessTimeCalculator(workSchedule);

    /**
     * Cria a aba “Gestão de Defeitos - Analítico” no workbook.
     *
     * @param wb            workbook destino
     * @param dataByProject mapa projeto → JSONArray de defeitos enriquecidos
     */
    public void create(Workbook wb, Map<String, JSONArray> dataByProject) {
        LoggerUtils.step("🐞 Criando planilha: Gestão de Defeitos - Analítico");

        Sheet sheet = wb.createSheet("Gestão de Defeitos - Analítico");
        ReportStyleManager styles = ReportStyleManager.from(wb);
        int rowNum = 0;

        // Cabeçalhos da planilha
        String[] headers = {
                "Projeto", "Funcionalidade", "Título", "Ticket",
                "Status", "Severidade", "Criado em",
                "Reportado por", "Reportado em", "Tempo em aberto", "Resolvido em", "Tempo de Resolução"
        };

        // === Cabeçalho ===
        Row headerRow = sheet.createRow(rowNum++);
        CellStyle headerStyle = styles.get("header");

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int totalDefects = 0;

        // Ordena projetos alfabeticamente
        List<String> projects = new ArrayList<>(dataByProject.keySet());
        Collections.sort(projects);

        for (int pIdx = 0; pIdx < projects.size(); pIdx++) {
            String projectCode = projects.get(pIdx);
            JSONArray defectsArray = dataByProject.get(projectCode);
            if (defectsArray == null || defectsArray.isEmpty()) continue;

            // Ordenação interna por suite e título
            List<JSONObject> rows = new ArrayList<>();
            for (int i = 0; i < defectsArray.length(); i++) {
                rows.add(defectsArray.getJSONObject(i));
            }
            rows.sort(Comparator.comparing((JSONObject o) -> o.optString("suite", ""))
                    .thenComparing(o -> o.optString("title", "")));

            for (JSONObject defect : rows) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // Dados principais
                String suite = defect.optString("suite", "Não identificada");
                String title = defect.optString("title", "");
                String ticket = defect.optString("ticket", "N/A");
                String status = defect.optString("status", "");
                String severity = defect.optString("severity", "");
                String createdAtIso = defect.optString("created_at", "");
                String reportDateIso = defect.optString("report_date_iso", "");
                String reportedBy = defect.optString("reported_by", "Desconhecido");
                String resolvedAtIso = defect.optString("resolved_at", "");

                createStyledCell(row, col++, projectCode, styles.get("left"));
                createStyledCell(row, col++, suite, styles.get("left"));
                createStyledCell(row, col++, title, styles.get("left"));
                createStyledCell(row, col++, ticket, styles.get("left"));
                createStyledCell(row, col++, status, styles.get("center"));
                createStyledCell(row, col++, severity, styles.get("center"));
                createStyledCell(row, col++, formatDate(createdAtIso), styles.get("center"));
                createStyledCell(row, col++, reportedBy, styles.get("left"));
                createStyledCell(row, col++, formatDate(reportDateIso), styles.get("center"));

                // === Tempo em aberto ===
                String openTime = "";
                if ((resolvedAtIso == null || resolvedAtIso.isBlank())
                        && reportDateIso != null && !reportDateIso.isBlank()) {
                    LocalDateTime start = parseIsoToLocalDateTime(reportDateIso);
                    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                    if (start != null && now.isAfter(start)) {
                        openTime = businessTimeCalculator.calculateBusinessTime(start, now);
                    }
                }
                createStyledCell(row, col++, openTime, styles.get("center"));

                // === Resolvido em ===
                createStyledCell(row, col++, formatDate(resolvedAtIso), styles.get("center"));

                // === Tempo de Resolução ===
                String resolutionTime = "";
                if (resolvedAtIso != null && !resolvedAtIso.isBlank()
                        && reportDateIso != null && !reportDateIso.isBlank()) {
                    resolutionTime = calculateDeltaHHmm(reportDateIso, resolvedAtIso);
                }
                createStyledCell(row, col++, resolutionTime, styles.get("center"));

                totalDefects++;
            }

            // Linha em branco entre projetos
            if (pIdx < projects.size() - 1) {
                rowNum++;
            }
        }

        // === Ajuste automático da largura das colunas com padding e mínimo para datas ===
        ReportStyleManager.autoSizeColumnsWithPadding(sheet, headers.length, ReportStyleManager.getDefaultPadding());

        LoggerUtils.success("📊 Planilha 'Gestão de Defeitos - Analítico' criada (" + totalDefects + " registros).");
    }

    /** Cria uma célula e aplica estilo. */
    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /** Formata string ISO-8601 para o padrão brasileiro dd/MM/yyyy HH:mm. */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            LocalDateTime ldt = odt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return ldt.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return "";
        }
    }

    /** Calcula o tempo de resolução (horas úteis) entre duas datas ISO. */
    private String calculateDeltaHHmm(String startIso, String endIso) {
        try {
            LocalDateTime start = parseIsoToLocalDateTime(startIso);
            LocalDateTime end = parseIsoToLocalDateTime(endIso);
            if (start == null || end == null) return "";
            if (end.isBefore(start)) return "00:00";
            return businessTimeCalculator.calculateBusinessTime(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    /** Converte ISO (com offset) para LocalDateTime no fuso da JVM. */
    private LocalDateTime parseIsoToLocalDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(iso);
            return odt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
