package com.sysmap.wellness.core.kpi.service;

import java.util.Objects;

/**
 * Representa o resumo consolidado de uma release para uso no
 * Painel Consolidado e em outros relatórios executivos.
 *
 * <p>
 * Cada instância desta classe corresponde a <b>exatamente uma linha</b> do
 * relatório, ou seja, uma combinação única de:
 * </p>
 * <ul>
 *   <li>Projeto;</li>
 *   <li>Release (officialId = version_environment);</li>
 *   <li>Escopo planejado (plannedScope);</li>
 *   <li>Casos executados (executedCases);</li>
 *   <li>Cobertura da release (%);</li>
 *   <li>Distribuição percentual dos resultados
 *       (Passed, Failed, Blocked, Skipped, Retest).</li>
 * </ul>
 *
 * <p>
 * Os campos de percentual são representados como {@code double} mas, por regra
 * de negócio atual, são sempre valores inteiros entre 0 e 100 (sem casas
 * decimais), já arredondados pelo agregador.
 * </p>
 */
public class ReleaseSummaryRow {

    private final String project;
    private final String releaseId;

    private final int plannedScope;
    private final int executedCases;

    private final double coveragePct;

    private final double passedPct;
    private final double failedPct;
    private final double blockedPct;
    private final double skippedPct;
    private final double retestPct;

    /**
     * Construtor principal. Recomenda-se o uso do {@link Builder}.
     */
    private ReleaseSummaryRow(Builder builder) {
        this.project = builder.project;
        this.releaseId = builder.releaseId;
        this.plannedScope = builder.plannedScope;
        this.executedCases = builder.executedCases;
        this.coveragePct = builder.coveragePct;
        this.passedPct = builder.passedPct;
        this.failedPct = builder.failedPct;
        this.blockedPct = builder.blockedPct;
        this.skippedPct = builder.skippedPct;
        this.retestPct = builder.retestPct;
    }

    // =======================
    // GETTERS
    // =======================

    public String getProject() {
        return project;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public int getPlannedScope() {
        return plannedScope;
    }

    public int getExecutedCases() {
        return executedCases;
    }

    public double getCoveragePct() {
        return coveragePct;
    }

    public double getPassedPct() {
        return passedPct;
    }

    public double getFailedPct() {
        return failedPct;
    }

    public double getBlockedPct() {
        return blockedPct;
    }

    public double getSkippedPct() {
        return skippedPct;
    }

    public double getRetestPct() {
        return retestPct;
    }

    @Override
    public String toString() {
        return "ReleaseSummaryRow{" +
            "project='" + project + '\'' +
            ", releaseId='" + releaseId + '\'' +
            ", plannedScope=" + plannedScope +
            ", executedCases=" + executedCases +
            ", coveragePct=" + coveragePct +
            ", passedPct=" + passedPct +
            ", failedPct=" + failedPct +
            ", blockedPct=" + blockedPct +
            ", skippedPct=" + skippedPct +
            ", retestPct=" + retestPct +
            '}';
    }

    // =======================
    // BUILDER
    // =======================

    /**
     * Builder para criação fluente e segura de {@link ReleaseSummaryRow}.
     *
     * <p>
     * O builder aplica validações mínimas (como normalização de valores
     * negativos para zero) para evitar estados inválidos no DTO.
     * </p>
     */
    public static class Builder {

        private String project;
        private String releaseId;

        private int plannedScope;
        private int executedCases;

        private double coveragePct;

        private double passedPct;
        private double failedPct;
        private double blockedPct;
        private double skippedPct;
        private double retestPct;

        /**
         * Define o código do projeto (ex.: FULLY, CHUBB).
         */
        public Builder withProject(String project) {
            this.project = project;
            return this;
        }

        /**
         * Define o identificador oficial da release (ex.: 3.3.0_STAGE).
         */
        public Builder withReleaseId(String releaseId) {
            this.releaseId = releaseId;
            return this;
        }

        /**
         * Define o escopo planejado da release (total de casos planejados).
         */
        public Builder withPlannedScope(int plannedScope) {
            this.plannedScope = Math.max(0, plannedScope);
            return this;
        }

        /**
         * Define o total de casos executados na release.
         */
        public Builder withExecutedCases(int executedCases) {
            this.executedCases = Math.max(0, executedCases);
            return this;
        }

        /**
         * Define a cobertura da release em percentual (0–100).
         */
        public Builder withCoveragePct(double coveragePct) {
            this.coveragePct = normalizePercent(coveragePct);
            return this;
        }

        public Builder withPassedPct(double passedPct) {
            this.passedPct = normalizePercent(passedPct);
            return this;
        }

        public Builder withFailedPct(double failedPct) {
            this.failedPct = normalizePercent(failedPct);
            return this;
        }

        public Builder withBlockedPct(double blockedPct) {
            this.blockedPct = normalizePercent(blockedPct);
            return this;
        }

        public Builder withSkippedPct(double skippedPct) {
            this.skippedPct = normalizePercent(skippedPct);
            return this;
        }

        public Builder withRetestPct(double retestPct) {
            this.retestPct = normalizePercent(retestPct);
            return this;
        }

        private double normalizePercent(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return 0.0;
            }
            // Não deixa negativo nem acima de 100, para evitar lixo em relatório
            if (v < 0.0) return 0.0;
            if (v > 100.0) return 100.0;
            return v;
        }

        public ReleaseSummaryRow build() {
            Objects.requireNonNull(project, "project não pode ser nulo");
            Objects.requireNonNull(releaseId, "releaseId não pode ser nulo");
            return new ReleaseSummaryRow(this);
        }
    }
}
