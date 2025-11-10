package com.sysmap.wellness.report.style;

import org.apache.poi.ss.usermodel.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Gerenciador central de estilos de planilhas do projeto WellnessQAReporter.
 * <p>
 * Essa classe substitui a antiga {@code ExcelStyleFactory} e padroniza
 * a aparência de todos os relatórios gerados (fonte, alinhamento, cores e bordas).
 * </p>
 * <p>
 * O uso é simples:
 * <pre>{@code
 * ReportStyleManager styles = ReportStyleManager.from(workbook);
 * CellStyle header = styles.get("header");
 * }</pre>
 */
public class ReportStyleManager {

    private final Workbook workbook;
    private final Map<String, CellStyle> styles = new HashMap<>();

    private ReportStyleManager(Workbook workbook) {
        this.workbook = workbook;
        initStyles();
    }

    /**
     * Inicializa o gerenciador de estilos com base no workbook fornecido.
     *
     * @param wb Workbook alvo
     * @return Instância de ReportStyleManager pronta para uso
     */
    public static ReportStyleManager from(Workbook wb) {
        return new ReportStyleManager(wb);
    }

    /** Inicializa e armazena os estilos padrão. */
    private void initStyles() {
        Font normalFont = workbook.createFont();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        // === Header ===
        CellStyle header = baseStyle();
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setFont(boldFont);
        styles.put("header", header);

        // === Left normal ===
        CellStyle left = baseStyle();
        left.setAlignment(HorizontalAlignment.LEFT);
        left.setFont(normalFont);
        styles.put("left", left);

        // === Left bold ===
        CellStyle leftBold = baseStyle();
        leftBold.setAlignment(HorizontalAlignment.LEFT);
        leftBold.setFont(boldFont);
        styles.put("leftBold", leftBold);

        // === Center ===
        CellStyle center = baseStyle();
        center.setAlignment(HorizontalAlignment.CENTER);
        center.setFont(normalFont);
        styles.put("center", center);

        // === Center bold ===
        CellStyle centerBold = baseStyle();
        centerBold.setAlignment(HorizontalAlignment.CENTER);
        centerBold.setFont(boldFont);
        styles.put("centerBold", centerBold);

        // === Total (bold + cinza) ===
        CellStyle total = baseStyle();
        total.setAlignment(HorizontalAlignment.CENTER);
        total.setVerticalAlignment(VerticalAlignment.CENTER);
        total.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        total.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        total.setFont(boldFont);
        styles.put("total", total);
    }

    /** Cria o estilo base com bordas finas. */
    private CellStyle baseStyle() {
        CellStyle s = workbook.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    /**
     * Retorna um estilo pelo nome.
     *
     * @param key Nome do estilo (header, left, leftBold, center, centerBold, total)
     * @return Estilo correspondente ou null se não existir
     */
    public CellStyle get(String key) {
        return styles.get(key);
    }
}
