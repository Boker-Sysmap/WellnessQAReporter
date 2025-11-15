package com.sysmap.wellness.report.service.model;

import org.json.JSONObject;
import java.time.LocalDateTime;

/**
 * Modelo de dados para um indicador (KPI).
 * Versão PREMIUM com construtor de compatibilidade.
 */
public class KPIData {

    private final String key;             // Identificador único do KPI
    private final String name;            // Nome amigável
    private final double value;           // Valor numérico bruto
    private final String formattedValue;  // Representação formatada
    private final String trendSymbol;     // ↑ ↓ →
    private final String description;     // Explicação do KPI
    private final boolean percent;        // Indica se é percentual
    private final String project;         // Código do projeto (pode ser null)
    private final String group;           // Categoria do KPI (pode ser null)
    private final LocalDateTime calculatedAt; // Timestamp

    // ================================================================
    // 🔵 NOVO CONSTRUTOR PRINCIPAL (completo)
    // ================================================================
    public KPIData(
            String key,
            String name,
            double value,
            String formattedValue,
            String trendSymbol,
            String description,
            boolean percent,
            String project,
            String group
    ) {
        this.key = key;
        this.name = name;
        this.value = value;
        this.formattedValue = formattedValue;
        this.trendSymbol = trendSymbol;
        this.description = description;
        this.percent = percent;
        this.project = project;
        this.group = group;
        this.calculatedAt = LocalDateTime.now();
    }

    // ================================================================
    // 🔵 CONSTRUTOR DE COMPATIBILIDADE (versão antiga)
    // ================================================================
    public KPIData(String name, double value, String trendSymbol, String description, boolean percent) {
        this.key = normalizeKey(name);
        this.name = name;
        this.value = value;
        this.trendSymbol = trendSymbol;
        this.description = description;
        this.percent = percent;
        this.project = null;
        this.group = null;
        this.calculatedAt = LocalDateTime.now();

        // construímos formattedValue automaticamente
        this.formattedValue = percent
                ? String.format("%.2f%%", value)
                : String.valueOf(value);
    }

    // gera chave automática baseada no nome do KPI
    private String normalizeKey(String name) {
        if (name == null) return "kpi";
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", "");
    }

    // ================================================================
    // GETTERS
    // ================================================================
    public String getKey() { return key; }
    public String getName() { return name; }
    public double getValue() { return value; }
    public String getFormattedValue() { return formattedValue; }
    public String getTrendSymbol() { return trendSymbol; }
    public String getDescription() { return description; }
    public boolean isPercent() { return percent; }
    public String getProject() { return project; }
    public String getGroup() { return group; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }

    // ================================================================
    // JSON EXPORT – para histórico
    // ================================================================
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("key", key);
        json.put("name", name);
        json.put("value", value);
        json.put("formattedValue", formattedValue);
        json.put("trendSymbol", trendSymbol != null ? trendSymbol : JSONObject.NULL);
        json.put("description", description);
        json.put("percent", percent);
        json.put("project", project != null ? project : JSONObject.NULL);
        json.put("group", group != null ? group : JSONObject.NULL);
        json.put("calculatedAt", calculatedAt.toString());
        return json;
    }
}
