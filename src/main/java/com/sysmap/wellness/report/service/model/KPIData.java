package com.sysmap.wellness.report.service.model;

/**
 * Modelo de dados para um indicador (KPI).
 */
public class KPIData {
    private final String name;
    private final double value;
    private final String trendSymbol;
    private final String description;
    private final boolean percent; // true se o valor for percentual (0–100)

    public KPIData(String name, double value, String trendSymbol, String description, boolean percent) {
        this.name = name;
        this.value = value;
        this.trendSymbol = trendSymbol;
        this.description = description;
        this.percent = percent;
    }

    public String getName() { return name; }
    public double getValue() { return value; }
    public String getTrendSymbol() { return trendSymbol; }
    public String getDescription() { return description; }
    public boolean isPercent() { return percent; }
}
