package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;
import java.util.Map;

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
 *     <li><b>Valor</b> – valor do KPI; o formato depende do tipo do indicador.</li>
 * </ul>
 *
 * <p>A aba é criada separadamente para cada projeto, normalmente nomeada como
 * “{Projeto} – Resumo Executivo” pelo {@code ReportGenerator}.</p>
 *
 * <h2>Regras e comportamentos importantes</h2>
 * <ul>
 *     <li>Os KPIs exibidos dependem apenas da lista recebida no parâmetro {@code kpis};</li>
 *     <li>Todos os registros usam o mesmo {@code currentReleaseId};</li>
 *     <li>A ordem dos KPIs exibidos é a mesma recebida pelo {@code List<KPIData>};</li>
 *     <li>Valores são formatados de acordo com o tipo do KPI:
 *          <ul>
 *              <li><b>plannedScope</b> → inteiro sem casas decimais;</li>
 *              <li><b>releaseCoverage</b> → porcentagem inteira com símbolo (%);</li>
 *              <li>Demais KPIs → formato numérico padrão (0.00).</li>
 *          </ul>
 *     </li>
 * </ul>
 *
 * <p>É uma aba essencial da visão executiva, complementando o Painel Consolidado.</p>
 */
public class ExecutiveKPISheet {

    /**
     * Labels amigáveis centralizados para exibição no Resumo Executivo.
     */
    private static final Map<String, String> KPI_LABELS = Map.of(
        "plannedScope", "Escopo planejado",
        "releaseCoverage", "Cobertura da Release"
    );

    /**
     * Cria a aba de Resumo Executivo dentro do workbook.
     *
     * <p>Fluxo:</p>
     * <ol>
     *     <li>Cria a planilha com o nome especificado;</li>
     *     <li>Define estilos numéricos adequados (inteiro, porcentagem, decimal);</li>
     *     <li>Insere o cabeçalho com Release, KPI e Valor;</li>
     *     <li>Insere as linhas conforme recebidas na lista de KPIs;</li>
     *     <li>Formata cada KPI conforme seu tipo.</li>
     * </ol>
     *
     * @param wb               Workbook onde a aba será criada.
     * @param kpis             Lista de {@link KPIData} calculados no run atual (ordem preservada).
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

        // Estilo numérico padrão (0.00)
        CellStyle decimalStyle = wb.createCellStyle();
        DataFormat df = wb.createDataFormat();
        decimalStyle.setDataFormat(df.getFormat("0.00"));

        // Estilo inteiro sem casas decimais
        CellStyle integerStyle = wb.createCellStyle();
        integerStyle.setDataFormat(df.getFormat("0"));

        // Estilo texto (para exibir "32%")
        CellStyle percentTextStyle = wb.createCellStyle();
        percentTextStyle.setDataFormat(df.getFormat("@")); // texto puro

        // Cabeçalho
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Release");
        header.createCell(1).setCellValue("KPI");
        header.createCell(2).setCellValue("Valor");

        int rowIndex = 1;

        // ============================================================
        // Popula cada KPI em uma linha (ordem 100% preservada)
        // ============================================================
        for (KPIData kpi : kpis) {

            String key = kpi.getKey();
            String label = KPI_LABELS.getOrDefault(key, kpi.getName());

            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(currentReleaseId);
            row.createCell(1).setCellValue(label);

            // Célula de valor com formatação dependente do KPI
            Cell valCell = row.createCell(2);

            switch (key) {

                case "plannedScope":
                    // inteiro sem casas decimais
                    valCell.setCellValue(kpi.getValue());
                    valCell.setCellStyle(integerStyle);
                    break;

                case "releaseCoverage":
                    // porcentagem inteira com símbolos — ex.: "32%"
                    String percentStr = ((int) kpi.getValue()) + "%";
                    valCell.setCellValue(percentStr);
                    valCell.setCellStyle(percentTextStyle);
                    break;

                default:
                    // demais KPIs → formato decimal padrão
                    valCell.setCellValue(kpi.getValue());
                    valCell.setCellStyle(decimalStyle);
                    break;
            }
        }
    }
}
