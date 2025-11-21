package com.sysmap.wellness.core.kpi.service;

import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.service.model.ReleaseContext;
import com.sysmap.wellness.utils.LoggerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

/**
 * Responsável por gerar snapshots históricos de KPIs por release.
 *
 * <p>Um snapshot representa o estado de um KPI em um instante de tempo,
 * incluindo seu contexto de release e todos os campos do {@link KPIData}.
 *
 * <p>Este componente não aplica qualquer regra de congelamento ou comparação
 * entre releases. Sempre insere ou substitui snapshots conforme a combinação
 * (officialId, kpiKey).
 */
public class HistoryWriter {

    /**
     * Cria um snapshot histórico completo, representando um KPI em um instante.
     *
     * @param releaseContext metadados da release
     * @param kpiData        valores do KPI
     * @return snapshot JSON contendo release + KPI + valores
     */
    public JSONObject createSnapshot(ReleaseContext releaseContext, KPIData kpiData) {

        JSONObject snapshot = new JSONObject();
        snapshot.put("timestamp", Instant.now().toString());

        // =========================
        // RELEASE CONTEXT
        // =========================
        if (releaseContext != null) {
            snapshot.put("officialId", releaseContext.getOfficialId());
            snapshot.put("version", releaseContext.getVersion());
            snapshot.put("environment", releaseContext.getEnvironment());
            snapshot.put("sprint", releaseContext.getSprint());
            snapshot.put("platform", releaseContext.getPlatform());
            snapshot.put("language", releaseContext.getLanguage());
            snapshot.put("testType", releaseContext.getTestType());
            snapshot.put("projectKey", releaseContext.getProjectKey());
            snapshot.put("releaseName", releaseContext.getReleaseName());

            if (releaseContext.getDate() != null) {
                snapshot.put("releaseDate", releaseContext.getDate().toString());
            }
        }

        // =========================
        // KPI DATA
        // =========================
        if (kpiData != null) {

            snapshot.put("kpiKey", kpiData.getKey());
            snapshot.put("kpiName", kpiData.getName());
            snapshot.put("kpiProject", kpiData.getProject());
            snapshot.put("kpiGroup", kpiData.getGroup());

            try {
                snapshot.put("values", kpiData.toJson());
            } catch (Exception e) {
                LoggerUtils.warn("HistoryWriter - falha ao converter KPIData.toJson(): " + e.getMessage());
                snapshot.put("values", new JSONObject());
            }
        }

        return snapshot;
    }

    /**
     * Insere ou atualiza o snapshot de uma release.
     *
     * <p>O critério de existência é baseado na combinação:
     * (officialId, kpiKey).
     *
     * <p>Se já existir um snapshot dessa combinação,
     * ele será substituído. Caso contrário, será inserido.
     *
     * @param history     array histórico existente (pode ser null)
     * @param newSnapshot novo snapshot a registrar
     * @return o próprio array histórico atualizado
     */
    public JSONArray upsertSnapshot(JSONArray history, JSONObject newSnapshot) {

        if (history == null)
            history = new JSONArray();

        if (newSnapshot == null)
            return history;

        String officialId = newSnapshot.optString("officialId", "");
        String kpiKey = newSnapshot.optString("kpiKey", "");

        if (officialId.isEmpty()) {
            LoggerUtils.warn("HistoryWriter.upsertSnapshot - snapshot ignorado (sem officialId).");
            return history;
        }

        // ============================================
        // Procurar snapshot existente (officialId + kpiKey)
        // ============================================
        for (int i = 0; i < history.length(); i++) {
            JSONObject snap = history.optJSONObject(i);
            if (snap == null) continue;

            boolean sameRelease = officialId.equals(snap.optString("officialId", ""));
            boolean sameKey = kpiKey.equals(snap.optString("kpiKey", ""));

            if (sameRelease && sameKey) {
                // Substitui por snapshot novo
                history.put(i, newSnapshot);
                return history;
            }
        }

        // Caso não exista → adicionar
        history.put(newSnapshot);
        return history;
    }

    /**
     * Verifica se já existe snapshot de um KPI específico para a release informada.
     */
    private boolean containsSnapshot(JSONArray history, String officialId, String kpiKey) {

        if (history == null) return false;

        for (int i = 0; i < history.length(); i++) {
            JSONObject snap = history.optJSONObject(i);
            if (snap == null) continue;

            boolean sameRelease = officialId.equals(snap.optString("officialId", ""));
            boolean sameKey = kpiKey.equals(snap.optString("kpiKey", ""));

            if (sameRelease && sameKey) {
                return true;
            }
        }

        return false;
    }
}
