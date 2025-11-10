package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.style.ReportStyleManager;
import com.sysmap.wellness.util.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gera a planilha "Gestão de Defeitos - Analítico" com formatação padronizada.
 * <p>
 * Essa versão utiliza {@link ReportStyleManager} para aplicar estilos consistentes
 * (bordas, alinhamento e fontes) em todas as células, garantindo o mesmo padrão
 * visual dos demais relatórios (ex: Resumo por Funcionalidade).
 * </p>
 *
 * <h3>Colunas:</h3>
 * Projeto | Funcionalidade | Título | Ticket | Status | Severidade |
 * Criado em | Reportado por | Reportado em | Resolvido em | Tempo de Resolução
 *
 * <h3>Regras:</h3>
 * <ul>
 *     <li>Insere linha em branco entre projetos.</li>
 *     <li>Ordena por projeto e em seguida por funcionalidade (suite.title).</li>
 *     <li>Formata datas no padrão brasileiro (dd/MM/yyyy HH:mm).</li>
 *     <li>Calcula o tempo de resolução (HH:mm) apenas quando {@code resolved_at} estiver presente.</li>
 * </ul>
 */
public class DefectAnalyticalReportSheet {

    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
                "Reportado por", "Reportado em", "Resolvido em", "Tempo de Resolução"
        };

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

                // Dados
                String suite = defect.optString("suite", "Não identificada");
                String title = defect.optString("title", "");
                String ticket = defect.optString("ticket", "N/A");
                String status = defect.optString("status", "");
                String severity = defect.optString("severity", "");
                String createdAtIso = defect.optString("created_at", "");
                String reportDateIso = defect.optString("report_date_iso", "");
                String reportedBy = defect.optString("reported_by", "Desconhecido");
                String resolvedAtIso = defect.optString("resolved_at", "");

                // === Preenche células com estilos ===
                row.createCell(col).setCellValue(projectCode);
                row.getCell(col++).setCellStyle(styles.get("left"));

                row.createCell(col).setCellValue(suite);
                row.getCell(col++).setCellStyle(styles.get("left"));

                row.createCell(col).setCellValue(title);
                row.getCell(col++).setCellStyle(styles.get("left"));

                row.createCell(col).setCellValue(ticket);
                row.getCell(col++).setCellStyle(styles.get("left"));

                row.createCell(col).setCellValue(status);
                row.getCell(col++).setCellStyle(styles.get("center"));

                row.createCell(col).setCellValue(severity);
                row.getCell(col++).setCellStyle(styles.get("center"));

                row.createCell(col).setCellValue(formatDate(createdAtIso));
                row.getCell(col++).setCellStyle(styles.get("center"));

                row.createCell(col).setCellValue(reportedBy);
                row.getCell(col++).setCellStyle(styles.get("left"));

                row.createCell(col).setCellValue(formatDate(reportDateIso));
                row.getCell(col++).setCellStyle(styles.get("center"));

                row.createCell(col).setCellValue(formatDate(resolvedAtIso));
                row.getCell(col++).setCellStyle(styles.get("center"));

                // Tempo de resolução (HH:mm)
                String timeDelta = "";
                if (resolvedAtIso != null && !resolvedAtIso.isBlank() &&
                        reportDateIso != null && !reportDateIso.isBlank()) {
                    timeDelta = calculateDeltaHHmm(reportDateIso, resolvedAtIso);
                }
                row.createCell(col).setCellValue(timeDelta);
                row.getCell(col).setCellStyle(styles.get("center"));

                totalDefects++;
            }

            // Linha em branco entre projetos (somente se não for o último)
            if (pIdx < projects.size() - 1) {
                rowNum++;
            }
        }

        // Ajuste automático da largura das colunas
        for (int c = 0; c < headers.length; c++) {
            sheet.autoSizeColumn(c);
        }

        LoggerUtils.success("📊 Planilha 'Gestão de Defeitos - Analítico' criada (" + totalDefects + " registros).");
    }

    /**
     * Formata string ISO-8601 para o padrão brasileiro dd/MM/yyyy HH:mm.
     *
     * @param isoDate data no formato ISO
     * @return data formatada ou string vazia
     */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            return odt.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calcula a diferença entre duas datas ISO em horas e minutos (HH:mm).
     * Retorna "00:00" caso a data final seja anterior à inicial.
     *
     * @param startIso data/hora inicial (ISO)
     * @param endIso   data/hora final (ISO)
     * @return tempo decorrido formatado em HH:mm
     */
    private String calculateDeltaHHmm(String startIso, String endIso) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(startIso);
            OffsetDateTime end = OffsetDateTime.parse(endIso);

            if (end.isBefore(start)) {
                return "00:00";
            }

            Duration d = Duration.between(start, end);
            long hours = d.toHours();
            long minutes = d.toMinutesPart();

            return String.format("%02d:%02d", hours, minutes);
        } catch (Exception e) {
            return "";
        }
    }
}
