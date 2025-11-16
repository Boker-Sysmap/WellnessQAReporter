package com.sysmap.wellness.report.service.model;

import org.json.JSONObject;
import java.time.LocalDateTime;

public class KPIData {

    private final String key;
    private final String name;
    private final double value;
    private final String formattedValue;
    private final String trendSymbol;
    private final String description;
    private final boolean percent;
    private final String project;
    private final String group;
    private final LocalDateTime calculatedAt;

    // CONSTRUTOR COMPLETO
    public KPIData(String key, String name, double value, String formattedValue,
                   String trendSymbol, String description, boolean percent,
                   String project, String group) {

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

    // Factory simplificado
    public static KPIData simple(String name, double value, String project, String group) {

        String key = name.toLowerCase()
            .replace(" ", "_")
            .replace("-", "_")
            .replaceAll("[^a-z0-9_]", "");

        String formatted = String.format("%.0f", value);   // sem decimais

        return new KPIData(
            key,
            name,
            value,
            formatted,
            "→",
            name,
            false,
            project,
            group
        );
    }

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

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("key", key);
        json.put("name", name);
        json.put("value", value);
        json.put("formattedValue", formattedValue);
        json.put("trendSymbol", trendSymbol);
        json.put("description", description);
        json.put("percent", percent);
        json.put("project", project);
        json.put("group", group);
        json.put("calculatedAt", calculatedAt.toString());
        return json;
    }
}
