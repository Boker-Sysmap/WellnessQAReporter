package com.sysmap.wellness.report.style;

import org.apache.poi.ss.usermodel.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Gerenciador central de estilos de planilhas do projeto WellnessQAReporter.
 * <p>
 * Responsável por padronizar fontes, bordas, alinhamentos e também
 * utilitários visuais (ajuste automático de colunas com padding configurável).
 * </p>
 */
public class ReportStyleManager {

    private final Workbook workbook;
    private final Map<String, CellStyle> styles = new HashMap<>();
    private static int defaultPadding = 2; // padrão em "caracteres"

    static {
        // tenta carregar o padding a partir do config.properties
        try (InputStream in = ReportStyleManager.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {

            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String val = props.getProperty("report.column.padding", "2").trim();
                defaultPadding = Integer.parseInt(val);
            }
        } catch (Exception e) {
            defaultPadding = 2; // fallback padrão
        }
    }

    private ReportStyleManager(Workbook workbook) {
        this.workbook = workbook;
        initStyles();
    }

    /** Inicializa o gerenciador de estilos com base no workbook fornecido. */
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
        s.setWrapText(false); // não quebra linhas automaticamente
        return s;
    }

    /** Retorna um estilo pelo nome. */
    public CellStyle get(String key) {
        return styles.get(key);
    }

    /** Retorna o padding padrão carregado do config.properties. */
    public static int getDefaultPadding() {
        return defaultPadding;
    }

    // ============================================================
    // 📏 UTILITÁRIOS DE FORMATAÇÃO GLOBAL
    // ============================================================

    /**
     * Ajusta automaticamente a largura das colunas da planilha,
     * aplicando o padding configurado (default ou do config.properties).
     *
     * @param sheet       Planilha alvo
     * @param numColumns  Quantidade de colunas a ajustar
     */
    public static void autoSizeColumnsWithPadding(Sheet sheet, int numColumns) {
        autoSizeColumnsWithPadding(sheet, numColumns, defaultPadding);
    }

    /**
     * Ajusta automaticamente a largura das colunas da planilha com espaçamento configurável.
     * Também garante largura mínima de 20 caracteres para colunas que contenham datas.
     *
     * @param sheet         Planilha alvo
     * @param numColumns    Quantidade de colunas a ajustar
     * @param paddingChars  Quantidade de caracteres extras de largura
     */
    public static void autoSizeColumnsWithPadding(Sheet sheet, int numColumns, int paddingChars) {
        if (sheet == null || numColumns <= 0) return;

        // Expressões regulares para identificar formatos de data textual
        Pattern datePatternBR = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}(\\s+\\d{2}:\\d{2})?$");
        Pattern datePatternISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}(:\\d{2})?.*$");

        for (int c = 0; c < numColumns; c++) {
            // Primeiro, tenta autoajustar pelo conteúdo real
            try {
                sheet.autoSizeColumn(c);
            } catch (Exception ignored) {}

            int width = sheet.getColumnWidth(c);
            boolean isDateColumn = false;

            // Detecta se há dados de data/hora na coluna
            for (Row row : sheet) {
                if (row == null) continue;
                Cell cell = row.getCell(c);
                if (cell == null) continue;

                try {
                    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        isDateColumn = true;
                        break;
                    } else if (cell.getCellType() == CellType.STRING) {
                        String text = cell.getStringCellValue().trim();
                        if (datePatternBR.matcher(text).matches() || datePatternISO.matcher(text).matches()) {
                            isDateColumn = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Define largura mínima (20 caracteres para datas, 4 para outras)
            int minWidth = isDateColumn ? (20 * 256) : (4 * 256);
            int paddedWidth = width + (paddingChars * 256);

            // Aplica o maior entre o mínimo e o calculado, respeitando limite máximo do Excel
            int finalWidth = Math.max(minWidth, Math.min(paddedWidth, 255 * 256));
            sheet.setColumnWidth(c, finalWidth);
        }
    }
}
