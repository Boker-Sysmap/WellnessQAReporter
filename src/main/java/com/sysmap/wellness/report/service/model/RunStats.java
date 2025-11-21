package com.sysmap.wellness.report.service.model;

/**
 * Representa estatísticas normalizadas de uma Test Run no Qase.
 *
 * <p>Esta classe é o contrato único utilizado pelos KPIs para interpretar
 * resultados de execução, abstraindo diferenças e inconsistências do JSON
 * retornado pela API do Qase.</p>
 */
public class RunStats {

    private final int totalCases;
    private final int executedCases;
    private final int untestedCases;
    private final int passed;
    private final int failed;
    private final int blocked;
    private final int skipped;
    private final int retest;
    private final int invalid;
    private final int inProgress;

    private RunStats(Builder builder) {
        this.totalCases = builder.totalCases;
        this.executedCases = builder.executedCases;
        this.untestedCases = builder.untestedCases;
        this.passed = builder.passed;
        this.failed = builder.failed;
        this.blocked = builder.blocked;
        this.skipped = builder.skipped;
        this.retest = builder.retest;
        this.invalid = builder.invalid;
        this.inProgress = builder.inProgress;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public int getExecutedCases() {
        return executedCases;
    }

    public int getUntestedCases() {
        return untestedCases;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public int getBlocked() {
        return blocked;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getRetest() {
        return retest;
    }

    public int getInvalid() {
        return invalid;
    }

    public int getInProgress() {
        return inProgress;
    }

    /**
     * Builder para criação segura de instâncias imutáveis de {@link RunStats}.
     */
    public static class Builder {
        private int totalCases;
        private int executedCases;
        private int untestedCases;
        private int passed;
        private int failed;
        private int blocked;
        private int skipped;
        private int retest;
        private int invalid;
        private int inProgress;

        public Builder withTotalCases(int totalCases) {
            this.totalCases = Math.max(0, totalCases);
            return this;
        }

        public Builder withExecutedCases(int executedCases) {
            this.executedCases = Math.max(0, executedCases);
            return this;
        }

        public Builder withUntestedCases(int untestedCases) {
            this.untestedCases = Math.max(0, untestedCases);
            return this;
        }

        public Builder withPassed(int passed) {
            this.passed = Math.max(0, passed);
            return this;
        }

        public Builder withFailed(int failed) {
            this.failed = Math.max(0, failed);
            return this;
        }

        public Builder withBlocked(int blocked) {
            this.blocked = Math.max(0, blocked);
            return this;
        }

        public Builder withSkipped(int skipped) {
            this.skipped = Math.max(0, skipped);
            return this;
        }

        public Builder withRetest(int retest) {
            this.retest = Math.max(0, retest);
            return this;
        }

        public Builder withInvalid(int invalid) {
            this.invalid = Math.max(0, invalid);
            return this;
        }

        public Builder withInProgress(int inProgress) {
            this.inProgress = Math.max(0, inProgress);
            return this;
        }

        public RunStats build() {
            return new RunStats(this);
        }
    }
}
