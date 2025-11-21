package com.sysmap.wellness.report.service.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa o contexto lógico de uma release, extraído a partir do identificador
 * configurado (mnemônicos) e utilizado como fonte única de metadados pelos KPIs.
 *
 * <p>O identificador oficial da release é sempre {@code ${version}_${environment}},
 * enquanto os demais campos são metadados complementares (platform, language,
 * testType, sprint, date etc.).</p>
 */
public class ReleaseContext {

    private final String version;
    private final String environment;
    private final String sprint;
    private final String platform;
    private final String language;
    private final String testType;
    private final String projectKey;
    private final String releaseName;
    private final LocalDate date;

    /**
     * Construtor principal. Recomendado o uso do {@link Builder}.
     */
    private ReleaseContext(Builder builder) {
        this.version = builder.version;
        this.environment = builder.environment;
        this.sprint = builder.sprint;
        this.platform = builder.platform;
        this.language = builder.language;
        this.testType = builder.testType;
        this.projectKey = builder.projectKey;
        this.releaseName = builder.releaseName;
        this.date = builder.date;
    }

    public String getVersion() {
        return version;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getSprint() {
        return sprint;
    }

    public String getPlatform() {
        return platform;
    }

    public String getLanguage() {
        return language;
    }

    public String getTestType() {
        return testType;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public LocalDate getDate() {
        return date;
    }

    /**
     * Retorna o identificador oficial da release, no formato {@code version_environment}.
     *
     * @return identificador oficial da release.
     */
    public String getOfficialId() {
        String safeVersion = version != null ? version.trim() : "";
        String safeEnv = environment != null ? environment.trim() : "";
        return safeVersion + "_" + safeEnv;
    }

    @Override
    public String toString() {
        return "ReleaseContext{" +
            "version='" + version + '\'' +
            ", environment='" + environment + '\'' +
            ", sprint='" + sprint + '\'' +
            ", platform='" + platform + '\'' +
            ", language='" + language + '\'' +
            ", testType='" + testType + '\'' +
            ", projectKey='" + projectKey + '\'' +
            ", releaseName='" + releaseName + '\'' +
            ", date=" + date +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReleaseContext)) return false;
        ReleaseContext that = (ReleaseContext) o;
        return Objects.equals(getOfficialId(), that.getOfficialId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOfficialId());
    }

    /**
     * Builder para construção fluente de {@link ReleaseContext}.
     */
    public static class Builder {
        private String version;
        private String environment;
        private String sprint;
        private String platform;
        private String language;
        private String testType;
        private String projectKey;
        private String releaseName;
        private LocalDate date;

        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder withEnvironment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder withSprint(String sprint) {
            this.sprint = sprint;
            return this;
        }

        public Builder withPlatform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder withLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder withTestType(String testType) {
            this.testType = testType;
            return this;
        }

        public Builder withProjectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        public Builder withReleaseName(String releaseName) {
            this.releaseName = releaseName;
            return this;
        }

        public Builder withDate(LocalDate date) {
            this.date = date;
            return this;
        }

        public ReleaseContext build() {
            return new ReleaseContext(this);
        }
    }
}
