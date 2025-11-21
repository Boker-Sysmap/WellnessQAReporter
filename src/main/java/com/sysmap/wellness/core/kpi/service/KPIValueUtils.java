package com.sysmap.wellness.core.kpi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilitário para cálculos e formatações de valores de KPI.
 *
 * <p>Concentra regras de arredondamento e apresentação numérica, garantindo
 * consistência entre os cálculos realizados pelo WellnessQAReporter e os
 * valores exibidos nos relatórios (Excel).</p>
 */
public final class KPIValueUtils {

    private KPIValueUtils() {
        // Utilitário estático, não deve ser instanciado.
    }

    /**
     * Calcula a porcentagem {@code part / total * 100}, retornando 0 quando o total for 0.
     *
     * @param part  parte.
     * @param total total.
     * @return porcentagem em double, com duas casas decimais.
     */
    public static double calculatePercent(int part, int total) {
        if (total <= 0 || part < 0) {
            return 0.0d;
        }
        BigDecimal bdPart = BigDecimal.valueOf(part);
        BigDecimal bdTotal = BigDecimal.valueOf(total);

        BigDecimal result = bdPart
            .multiply(BigDecimal.valueOf(100))
            .divide(bdTotal, 2, RoundingMode.HALF_UP);

        return result.doubleValue();
    }

    /**
     * Formata a porcentagem {@code part / total * 100} com símbolo de porcentagem, ex.: "37%".
     *
     * @param part  parte.
     * @param total total.
     * @return string formatada, ex.: "0%", "100%".
     */
    public static String formatPercent(int part, int total) {
        double percent = calculatePercent(part, total);
        long rounded = Math.round(percent);
        return rounded + "%";
    }

    /**
     * Formata um valor decimal com número fixo de casas decimais.
     *
     * @param value valor numérico.
     * @param scale quantidade de casas decimais.
     * @return string com valor formatado.
     */
    public static String formatDecimal(double value, int scale) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(Math.max(0, scale), RoundingMode.HALF_UP);
        return bd.toPlainString();
    }
}
