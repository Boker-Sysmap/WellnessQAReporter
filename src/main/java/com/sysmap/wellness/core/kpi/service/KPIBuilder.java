package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.service.model.ReleaseContext;

import java.util.Objects;

/**
 * Builder compatível com a estrutura imutável de {@link KPIData}.
 * Não usa withValue(), pois KPIData não possui campos mutáveis.
 *
 * Este builder facilita a montagem de KPIs para os serviços do KPIEngine.
 */
public class KPIBuilder {

    private final String key;
    private final String name;

    private double value = 0.0;
    private String formattedValue = "";
    private String trendSymbol = "→";
    private String description = "";
    private boolean percent = false;

    private String project;
    private String group;

    public KPIBuilder(String key, String name) {
        this.key = Objects.requireNonNull(key);
        this.name = Objects.requireNonNull(name);
    }

    public KPIBuilder value(double v) {
        this.value = v;
        return this;
    }

    public KPIBuilder formattedValue(String fv) {
        this.formattedValue = fv;
        return this;
    }

    public KPIBuilder trendSymbol(String symbol) {
        this.trendSymbol = symbol;
        return this;
    }

    public KPIBuilder description(String desc) {
        this.description = desc;
        return this;
    }

    public KPIBuilder percent(boolean isPercent) {
        this.percent = isPercent;
        return this;
    }

    public KPIBuilder project(String project) {
        this.project = project;
        return this;
    }

    public KPIBuilder group(String group) {
        this.group = group;
        return this;
    }

    /**
     * Preenche automaticamente:
     * - group = officialId (version_environment)
     * - project = context.projectKey
     */
    public KPIBuilder fromReleaseContext(ReleaseContext ctx) {
        if (ctx != null) {
            this.group = ctx.getOfficialId();
            this.project = ctx.getProjectKey();
        }
        return this;
    }

    public KPIData build() {
        return new KPIData(
            key,
            name,
            value,
            formattedValue,
            trendSymbol,
            description,
            percent,
            project,
            group
        );
    }
}
