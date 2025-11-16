package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

/**
 * Gera a aba <b>Resumo Executivo</b> para um único projeto.
 *
 * <p>Esta planilha tem caráter sintético e exibe os KPIs calculados
 * na execução atual (run-based), sempre associados à release vigente.</p>
 *
 * <h2>Estrutura da planilha</h2>
 * <ul>
 *     <li><b>Release</b> – identifica a release correspondente aos KPIs;</li>
 *     <li><b>KPI</b> – nome amigável do indicador;</li>
 *     <li><b>Valor</b> – valor numérico do KPI (duas casas decimais).</li>
 * </ul>
 *
 * <p>A aba é criada separadamente para cada projeto, normalmente nomeada como
 * “{Projeto} – Resumo Executivo” pelo {@code ReportGenerator}.</p>
 *
 * <h2>Regras e comportamentos importantes</h2>
 * <ul>
 *     <li>Os KPIs exibidos dependem apenas da lista recebida no parâmetro {@code kpis};</li>
 *     <li>Todos os registros usam o mesmo {@code currentReleaseId};</li>
 *     <li>Os valores são tratados como numéricos e formatados com 2 casas decimais;</li>
 *     <li>A planilha não realiza ordenação nem extração de dados adicionais.</li>
 * </ul>
 *
 * <p>É uma aba essencial da visão executiva, complementando o Painel Consolidado.</p>
 */
public class ExecutiveKPISheet {

    /**
     * Cria a aba de Resumo Executivo dentro do workbook.
     *
     * <p>Fluxo:</p>
     * <ol>
     *     <li>Cria a planilha com o nome especificado;</li>
     *     <li>Define um estilo numérico padrão (0.00);</li>
     *     <li>Insere o cabeçalho com as três colunas principais;</li>
     *     <li>Insere uma linha para cada KPI informado;</li>
     *     <li>Atribui o ID da release para todas as linhas.</li>
     * </ol>
     *
     * @param wb               Workbook onde a aba será criada.
     * @param kpis             Lista de {@link KPIData} calculados no run atual.
     * @param sheetName        Nome da aba que será criada.
     * @param currentReleaseId Identificador da release ativa.
     */
    public static void create(
        XSSFWorkbook wb,
        List<KPIData> kpis,
        String sheetName,
        String currentReleaseId
    ) {
        Sheet sheet = wb.createSheet(sheetName);

        // Estilo numérico com duas casas decimais
        CellStyle numberStyle = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        numberStyle.setDataFormat(format.getFormat("0.00"));

        // Cabeçalho
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Release");
        header.createCell(1).setCellValue("KPI");
        header.createCell(2).setCellValue("Valor");

        int rowIndex = 1;

        // Popula cada KPI em uma linha
        for (KPIData kpi : kpis) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(currentReleaseId);
            row.createCell(1).setCellValue(kpi.getName());

            Cell valCell = row.createCell(2);
            valCell.setCellValue(kpi.getValue());
            valCell.setCellStyle(numberStyle);
        }
    }
}
