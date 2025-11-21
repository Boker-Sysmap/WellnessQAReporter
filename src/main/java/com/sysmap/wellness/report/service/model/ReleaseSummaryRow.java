package com.sysmap.wellness.report.service.model;

/**
 * Representa o resumo consolidado de uma release para exibição
 * em relatórios executivos (uma linha por release).
 *
 * <p>Esta classe NÃO contém regras de negócio; ela é apenas o
 * DTO de transporte entre a camada de KPI/negócio e a camada
 * de geração de relatório (sheets Excel).</p>
 *
 * <h2>Colunas esperadas no relatório:</h2>
 * <ul>
 *     <li>Projeto</li>
 *     <li>Release (officialId = version_environment)</li>
 *     <li>KPI 1 – Escopo planejado (casos)</li>
 *     <li>KPI 2 – Cobertura da Release (%)</li>
 *     <li>KPI 3 – Resultados da Release (% Passed, Failed, Blocked, Skipped, Retest)</li>
 * </ul>
 */
public class ReleaseSummaryRow {

    /** Projeto (ex.: FULLY, CHUBB, etc.) */
    private final String project;

    /** Identificador oficial da release: version_environment (ex.: 3.3.0_STAGE) */
    private final String releaseId;

    /** Escopo planejado: total de casos planejados (soma de cases_count dos plans da release) */
    private final int plannedScope;

    /** Cobertura da release: % de casos executados em relação ao escopo planejado */
    private final double coveragePct;

    /** % de casos executados com status PASSED em relação ao total executado */
    private final double passedPct;

    /** % de casos executados com status FAILED em relação ao total executado */
    private final double failedPct;

    /** % de casos executados com status BLOCKED em relação ao total executado */
    private final double blockedPct;

    /** % de casos executados com status SKIPPED em relação ao total executado */
    private final double skippedPct;

    /** % de casos executados com status RETEST em relação ao total executado */
    private final double retestPct;

    /**
     * Construtor completo.
     *
     * @param project      código do projeto (FULLY, CHUBB, etc.)
     * @param releaseId    identificador oficial da release (version_environment)
     * @param plannedScope escopo planejado (total de casos)
     * @param coveragePct  cobertura da release (% executados / escopo)
     * @param passedPct    % de casos executados em PASSED
     * @param failedPct    % de casos executados em FAILED
     * @param blockedPct   % de casos executados em BLOCKED
     * @param skippedPct   % de casos executados em SKIPPED
     * @param retestPct    % de casos executados em RETEST
     */
    public ReleaseSummaryRow(
        String project,
        String releaseId,
        int plannedScope,
        double coveragePct,
        double passedPct,
        double failedPct,
        double blockedPct,
        double skippedPct,
        double retestPct
    ) {
        this.project = project;
        this.releaseId = releaseId;
        this.plannedScope = Math.max(0, plannedScope);
        this.coveragePct = normalizePercent(coveragePct);
        this.passedPct = normalizePercent(passedPct);
        this.failedPct = normalizePercent(failedPct);
        this.blockedPct = normalizePercent(blockedPct);
        this.skippedPct = normalizePercent(skippedPct);
        this.retestPct = normalizePercent(retestPct);
    }

    /**
     * Normaliza percentuais evitando valores inválidos.
     */
    private double normalizePercent(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        return Math.max(0.0, v);
    }

    public String getProject() {
        return project;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public int getPlannedScope() {
        return plannedScope;
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
            ", coveragePct=" + coveragePct +
            ", passedPct=" + passedPct +
            ", failedPct=" + failedPct +
            ", blockedPct=" + blockedPct +
            ", skippedPct=" + skippedPct +
            ", retestPct=" + retestPct +
            '}';
    }
}
