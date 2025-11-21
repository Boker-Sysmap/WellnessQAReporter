package com.sysmap.wellness.core.excel.sheet;

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
 * Responsável pela geração da planilha <b>"Gestão de Defeitos - Analítico"</b>,
 * parte integrante do relatório completo do WellnessQAReporter.
 *
 * <p>Esta classe recebe os defeitos já normalizados pelo
 * {@code DefectAnalyticalService} e constrói uma aba Excel rica em detalhes,
 * com dados organizados por projeto e formatados de acordo com o padrão visual
 * definido pelo {@link ReportStyleManager}.</p>
 *
 * <h2>Principais responsabilidades</h2>
 * <ul>
 *   <li>Gerar cabeçalho padronizado da planilha;</li>
 *   <li>Organizar dados de defeitos agrupados por projeto;</li>
 *   <li>Formatar datas para o padrão DD/MM/YYYY HH:mm;</li>
 *   <li>Calcular:
 *     <ul>
 *       <li>tempo em aberto (open time),</li>
 *       <li>tempo de resolução,</li>
 *     </ul>
 *     considerando calendário de trabalho e horários úteis;</li>
 *   <li>Aplicar estilos visuais consistentes em todas as células;</li>
 *   <li>Ajustar automaticamente a largura das colunas.</li>
 * </ul>
 *
 * <p>Esta aba é uma das mais analíticas do relatório, oferecendo visão profunda
 * sobre o ciclo de vida de cada defeito.</p>
 */
public class DefectAnalyticalReportSheet {

    /** Formato final de exibição de datas no Excel. */
    private static final DateTimeFormatter OUTPUT_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Agenda padrão utilizada para cálculo de horas úteis. */
    private static final WorkSchedule workSchedule = new WorkSchedule();

    /** Serviço que calcula intervalos considerando apenas o horário comercial. */
    private static final BusinessTimeCalculator businessTimeCalculator =
        new BusinessTimeCalculator(workSchedule);

    /**
     * Cria a aba Excel completa contendo os detalhes analíticos dos defeitos.
     *
     * <p>Fluxo resumido:</p>
     * <ol>
     *   <li>Criar sheet com nome fornecido;</li>
     *   <li>Gerar cabeçalho com estilo;</li>
     *   <li>Para cada projeto, listar seus defeitos;</li>
     *   <li>Preencher colunas com dados enriquecidos pelo serviço anterior;</li>
     *   <li>Calcular tempos de ciclo quando necessário;</li>
     *   <li>Aplicar estilos e auto-size de colunas;</li>
     *   <li>Registrar logs de conclusão.</li>
     * </ol>
     *
     * @param wb           Workbook Excel onde a aba será criada.
     * @param dataByProject Mapa contendo defeitos agrupados por projeto.
     * @param sheetName    Nome da aba que será criada.
     */
    public void create(Workbook wb, Map<String, JSONArray> dataByProject, String sheetName) {
        LoggerUtils.step("🐞 Criando planilha: " + sheetName);

        Sheet sheet = wb.createSheet(sheetName);
        ReportStyleManager styles = ReportStyleManager.from(wb);
        int rowNum = 0;

        String[] headers = {
            "Projeto", "Funcionalidade", "Título", "Ticket",
            "Status", "Severidade", "Criado em",
            "Reportado por", "Reportado em", "Tempo em aberto", "Resolvido em", "Tempo de Resolução"
        };

        // ============================================================
        // Cabeçalho
        // ============================================================
        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }

        int totalDefects = 0;

        // Ordena os projetos para consistência
        List<String> projects = new ArrayList<String>(dataByProject.keySet());
        Collections.sort(projects);

        // ============================================================
        // Preenchimento dos registros
        // ============================================================
        for (String projectCode : projects) {
            JSONArray defectsArray = dataByProject.get(projectCode);
            if (defectsArray == null || defectsArray.length() == 0) continue;

            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                // Projeto
                createStyledCell(row, col++, projectCode, styles.get("left"));

                // Funcionalidade (suite)
                createStyledCell(row, col++, defect.optString("suite", "Não identificada"), styles.get("left"));

                // Título do defeito
                createStyledCell(row, col++, defect.optString("title", ""), styles.get("left"));

                // Ticket vinculado
                createStyledCell(row, col++, defect.optString("ticket", "N/A"), styles.get("left"));

                // Status
                createStyledCell(row, col++, defect.optString("status", ""), styles.get("center"));

                // Severidade
                createStyledCell(row, col++, defect.optString("severity", ""), styles.get("center"));

                // Criado em (ISO → formato exibível)
                createStyledCell(row, col++, formatDate(defect.optString("created_at", "")), styles.get("center"));

                // Reportado por
                createStyledCell(row, col++, defect.optString("reported_by", "Desconhecido"), styles.get("left"));

                // Data de reporte (ISO → formatado)
                String reportDateIso = defect.optString("report_date_iso", "");
                createStyledCell(row, col++, formatDate(reportDateIso), styles.get("center"));

                // ============================
                // Tempo em aberto (open_time)
                // ============================
                String resolvedAtIso = defect.optString("resolved_at", "");
                String openTime = defect.optString("open_time", "");

                // Se não vier pronto, calcula no ato
                if (openTime == null || openTime.trim().isEmpty()) {
                    if ((resolvedAtIso == null || resolvedAtIso.trim().isEmpty()) &&
                        reportDateIso != null && !reportDateIso.trim().isEmpty()) {

                        LocalDateTime start = parseIsoToLocalDateTime(reportDateIso);
                        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

                        if (start != null && now.isAfter(start)) {
                            openTime = businessTimeCalculator.calculateBusinessTime(start, now);
                        }
                    }
                }
                createStyledCell(row, col++, openTime, styles.get("center"));

                // Resolvido em
                createStyledCell(row, col++, formatDate(resolvedAtIso), styles.get("center"));

                // ============================
                // Tempo de Resolução
                // ============================
                String resolutionTime = defect.optString("resolution_time", "");

                // Calcula se não existir pronto
                if (resolutionTime == null || resolutionTime.trim().isEmpty()) {
                    if (resolvedAtIso != null && !resolvedAtIso.trim().isEmpty() &&
                        reportDateIso != null && !reportDateIso.trim().isEmpty()) {
                        resolutionTime = calculateDeltaHHmm(reportDateIso, resolvedAtIso);
                    }
                }

                createStyledCell(row, col++, resolutionTime, styles.get("center"));

                totalDefects++;
            }
        }

        // ============================================================
        // Ajuste final de colunas
        // ============================================================
        ReportStyleManager.autoSizeColumnsWithPadding(
            sheet,
            headers.length,
            ReportStyleManager.getDefaultPadding()
        );

        LoggerUtils.success("📊 Planilha '" + sheetName + "' criada (" + totalDefects + " registros).");
    }

    /**
     * Cria uma célula com estilo apropriado, preenchendo valor textual.
     *
     * @param row   Linha do Excel.
     * @param col   Índice da coluna.
     * @param value Conteúdo textual da célula.
     * @param style Estilo POI aplicado.
     */
    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Converte uma string ISO-8601 em data formatada para exibição
     * no padrão <code>dd/MM/yyyy HH:mm</code>.
     *
     * <p>Caso o valor seja inválido, retorna texto vazio.</p>
     *
     * @param isoDate Data no formato ISO.
     * @return Data formatada ou string vazia.
     */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            LocalDateTime ldt = odt.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
            return ldt.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Calcula o tempo transcorrido entre duas datas ISO, considerando apenas
     * horas úteis configuradas no {@link BusinessTimeCalculator}.
     *
     * @param startIso Data/hora inicial ISO.
     * @param endIso   Data/hora final ISO.
     * @return String no formato HH:mm ou vazia em caso de erro.
     */
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

    /**
     * Interpreta uma string ISO e converte para {@link LocalDateTime},
     * suportando tanto <code>OffsetDateTime</code> quanto <code>LocalDateTime</code>.
     *
     * @param iso String no formato ISO.
     * @return LocalDateTime correspondente ou null caso inválido.
     */
    private LocalDateTime parseIsoToLocalDateTime(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(iso);
            return odt.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(iso);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
