package com.sysmap.wellness.report.kpi.history;

import org.json.JSONObject;

import java.time.LocalDateTime;

/**
 * Registro de histórico de um KPI em uma release específica.
 */
public class KPIHistoryRecord {

    private final String kpiName;      // ex: escopo_planejado
    private final String project;      // ex: FULLYREPO
    private final String release;      // ex: FULLYREPO-2025-02-R01
    private final LocalDateTime timestamp;
    private final Object value;        // valor numérico original
    private final JSONObject details;  // JSON com campos extras (name, percent, etc.)

    public KPIHistoryRecord(
        String kpiName,
        String project,
        String release,
        LocalDateTime timestamp,
        Object value,
        JSONObject details
    ) {
        this.kpiName = kpiName;
        this.project = project;
        this.release = release;
        this.timestamp = timestamp;
        this.value = value;
        this.details = details != null ? details : new JSONObject();
    }

    public String getKpiName() {
        return kpiName;
    }

    public String getProject() {
        return project;
    }

    /** Nome da release (ex: FULLYREPO-2025-02-R01). */
    public String getRelease() {
        return release;
    }

    /** Alias semântico, se precisar em algum lugar. */
    public String getReleaseName() {
        return release;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Object getValue() {
        return value;
    }

    public JSONObject getDetails() {
        return details;
    }

    // =========================================================
    // SERIALIZAÇÃO JSON
    // =========================================================

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("kpiName", kpiName);
        json.put("project", project);
        json.put("release", release);
        json.put("timestamp", timestamp.toString());
        json.put("value", value);
        json.put("details", details);
        return json;
    }

    public static KPIHistoryRecord fromJson(JSONObject json) {
        String kpiName = json.optString("kpiName", null);
        String project = json.optString("project", null);
        String release = json.optString("release", null);
        String ts = json.optString("timestamp", null);
        LocalDateTime timestamp = ts != null ? LocalDateTime.parse(ts) : LocalDateTime.now();
        Object value = json.opt("value");
        JSONObject details = json.optJSONObject("details");
        return new KPIHistoryRecord(kpiName, project, release, timestamp, value, details);
    }
}
