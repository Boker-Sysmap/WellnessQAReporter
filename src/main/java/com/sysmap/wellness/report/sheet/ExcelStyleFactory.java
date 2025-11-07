package com.sysmap.wellness.report.sheet;

import org.apache.poi.ss.usermodel.*;

/**
 * Fábrica utilitária responsável pela criação e configuração de estilos de células
 * para planilhas Excel geradas com o Apache POI.
 *
 * <p>Esta classe centraliza a padronização visual de bordas, alinhamento,
 * cor de fundo e formatação de texto (como negrito),
 * garantindo consistência entre todas as abas do relatório.</p>
 *
 * <h2>Exemplo de uso:</h2>
 * <pre>{@code
 * Workbook wb = new XSSFWorkbook();
 * CellStyle headerStyle = ExcelStyleFactory.createStyle(
 *         wb, true, true, HorizontalAlignment.CENTER);
 * }</pre>
 *
 * <p>A classe é estática e não requer instanciação.</p>
 *
 * @author Roberto
 * @version 1.1
 * @since 1.0
 */
public class ExcelStyleFactory {

    /**
     * Cria e configura um {@link CellStyle} com as opções informadas.
     *
     * <p>Define bordas finas em todas as direções, centralização vertical
     * e alinhamento horizontal personalizado. Opcionalmente, pode aplicar
     * texto em negrito e fundo cinza claro.</p>
     *
     * @param wb              Instância do {@link Workbook} na qual o estilo será criado.
     * @param bold            Define se o texto da célula deve ser exibido em <b>negrito</b>.
     * @param grayBackground  Define se o fundo da célula deve ser <b>cinza-claro</b>.
     * @param align           Alinhamento horizontal a ser aplicado (ex: {@link HorizontalAlignment#CENTER}).
     * @return Um objeto {@link CellStyle} configurado conforme os parâmetros informados.
     */
    public static CellStyle createStyle(Workbook wb, boolean bold, boolean grayBackground, HorizontalAlignment align) {
        CellStyle style = wb.createCellStyle();

        // === Alinhamento e bordas padrão ===
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        // === Fundo cinza opcional ===
        if (grayBackground) {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        // === Fonte em negrito opcional ===
        if (bold) {
            Font font = wb.createFont();
            font.setBold(true);
            style.setFont(font);
        }

        return style;
    }
}
