package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.util.LoggerUtils;
import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Responsável pela criação da aba <b>"Gestão de Defeitos - Analítico"</b>
 * no relatório Excel gerado pelo sistema.
 *
 * <p>Esta classe utiliza os dados normalizados de defeitos fornecidos pelo
 * {@link com.sysmap.wellness.report.service.DefectAnalyticalService} para
 * preencher uma planilha detalhada, listando todos os defeitos por projeto,
 * com informações de status, severidade, datas e tempo de resolução.</p>
 *
 * <h2>Principais responsabilidades:</h2>
 * <ul>
 *   <li>Gerar uma aba de relatório detalhada para cada projeto;</li>
 *   <li>Preencher colunas padronizadas (ID, título, status, datas etc.);</li>
 *   <li>Calcular e formatar o tempo de resolução dos defeitos;</li>
 *   <li>Aplicar estilos de cabeçalho e dimensionamento automático das colunas.</li>
 * </ul>
 *
 * <p>O método {@link #create(Workbook, Map)} é o ponto de entrada principal.</p>
 *
 * @author Roberto
 * @version 1.1
 * @since 1.0
 */
public class DefectAnalyticalReportSheet {

    /** Formato padrão de saída para datas exibidas na planilha. */
    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Cria a planilha <b>"Gestão de Defeitos - Analítico"</b> dentro do workbook informado.
     *
     * <p>Para cada projeto presente em {@code dataByProject}, os defeitos são
     * iterados e lançados linha a linha com suas respectivas informações.</p>
     *
     * @param wb             {@link Workbook} do Apache POI onde a aba será criada.
     * @param dataByProject  Mapa contendo os defeitos organizados por projeto.
     *                       Estrutura: <code>projeto → JSONArray de defeitos</code>.
     */
    public void create(Workbook wb, Map<String, JSONArray> dataByProject) {
        LoggerUtils.step("🐞 Criando planilha: Gestão de Defeitos - Analítico");

        Sheet sheet = wb.createSheet("Gestão de Defeitos - Analítico");
        int rowNum = 0;

        // === Cabeçalhos da planilha ===
        String[] headers = {
                "Projeto", "ID", "Título", "Status", "Severidade", "Criado Por",
                "Criado em", "Atualizado em", "Resolvido em", "Tempo de Resolução", "Descrição"
        };

        Row header = sheet.createRow(rowNum++);
        CellStyle headerStyle = wb.createCellStyle();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int totalDefects = 0;

        // === Itera pelos projetos e preenche linhas ===
        for (var entry : dataByProject.entrySet()) {
            String projectCode = entry.getKey();
            JSONArray defectsArray = entry.getValue();

            if (defectsArray == null || defectsArray.isEmpty()) continue;

            for (int i = 0; i < defectsArray.length(); i++) {
                JSONObject defect = defectsArray.getJSONObject(i);
                Row row = sheet.createRow(rowNum++);
                int col = 0;

                String status = defect.optString("status", "");
                String resolvedAt = defect.optString("resolved_at", "");
                String createdAt = defect.optString("created_at", "");
                String updatedAt = defect.optString("updated_at", "");

                // === Preenche as colunas principais ===
                row.createCell(col++).setCellValue(projectCode);
                row.createCell(col++).setCellValue(defect.opt("id") != null ? defect.get("id").toString() : "");
                row.createCell(col++).setCellValue(defect.optString("title", ""));
                row.createCell(col++).setCellValue(status);
                row.createCell(col++).setCellValue(defect.optString("severity", ""));
                row.createCell(col++).setCellValue(defect.optString("created_by", ""));
                row.createCell(col++).setCellValue(formatDate(createdAt));
                row.createCell(col++).setCellValue(formatDate(updatedAt));

                // ✅ Coluna "Resolvido em" — preenche apenas se o status for "resolved"
                if ("resolved".equalsIgnoreCase(status) && !resolvedAt.isEmpty()) {
                    row.createCell(col++).setCellValue(formatDate(resolvedAt));
                } else {
                    row.createCell(col++).setCellValue("");
                }

                // 🕓 Tempo total entre criação e resolução
                row.createCell(col++).setCellValue(calculateResolutionTime(createdAt, resolvedAt));

                // 📄 Descrição (texto livre)
                row.createCell(col++).setCellValue(defect.optString("description", ""));
                totalDefects++;
            }

            LoggerUtils.success("📁 Projeto " + projectCode + " com " + defectsArray.length() + " defeitos carregados.");
        }

        // Ajusta automaticamente o tamanho das colunas
        for (int c = 0; c < headers.length; c++) {
            sheet.autoSizeColumn(c);
        }

        LoggerUtils.success("📊 Planilha 'Gestão de Defeitos - Analítico' criada (" + totalDefects + " registros).");
    }

    /**
     * Formata uma data ISO 8601 para o padrão <code>dd/MM/yyyy HH:mm:ss</code>.
     *
     * <p>Exemplo de entrada: <code>2025-10-15T13:45:30Z</code></p>
     * <p>Exemplo de saída: <code>15/10/2025 13:45:30</code></p>
     *
     * @param isoDate Data em formato ISO (string JSON).
     * @return Data formatada, ou a string original caso o parse falhe.
     */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoDate);
            return odt.format(OUTPUT_FORMATTER);
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Calcula o tempo total de resolução de um defeito com base
     * nas datas de criação e resolução.
     *
     * <p>O tempo é formatado no padrão:
     * <code>Xdd YYh ZZmin</code> — por exemplo, "2d 04h 15m".</p>
     *
     * @param createdIso  Data/hora de criação do defeito em formato ISO.
     * @param resolvedIso Data/hora de resolução do defeito em formato ISO.
     * @return String representando a duração formatada,
     *         ou vazia caso não seja possível calcular.
     */
    private String calculateResolutionTime(String createdIso, String resolvedIso) {
        if (createdIso == null || resolvedIso == null || createdIso.isEmpty() || resolvedIso.isEmpty())
            return "";
        try {
            OffsetDateTime start = OffsetDateTime.parse(createdIso);
            OffsetDateTime end = OffsetDateTime.parse(resolvedIso);
            Duration d = Duration.between(start, end);
            long days = d.toDays();
            long hours = d.toHours() % 24;
            long minutes = d.toMinutes() % 60;
            return String.format("%dd %02dh %02dm", days, hours, minutes);
        } catch (Exception e) {
            return "";
        }
    }
}
