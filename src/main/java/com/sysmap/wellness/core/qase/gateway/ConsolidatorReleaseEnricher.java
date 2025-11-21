package com.sysmap.wellness.core.qase.gateway;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Responsável por enriquecer os dados de plans/runs na fase de consolidação.
 *
 * <p>Funções atuais:</p>
 * <ul>
 *   <li>Indexar Test Plans por {@code id};</li>
 *   <li>Garantir que cada plan possua um array {@code runs};</li>
 *   <li>Associar Test Runs aos respectivos Test Plans via {@code plan_id}.</li>
 * </ul>
 *
 * <p>Este componente não interpreta identificadores de release; essa
 * responsabilidade permanece em camadas posteriores (por exemplo,
 * no KPIEngine/ReleaseMatcher).</p>
 */
public class ConsolidatorReleaseEnricher {

    /**
     * Índice interno de plans por ID, reconstruído a cada ciclo de consolidação.
     *
     * É populado quando o contexto é "plan" e reutilizado quando o contexto é "run".
     */
    private final Map<Integer, JSONObject> plansById = new HashMap<>();

    /**
     * Enriquecimento de entidades de acordo com o contexto.
     *
     * <p>Comportamento:</p>
     * <ul>
     *   <li>context = "plan": indexa plans por {@code id} e inicializa o array {@code runs};</li>
     *   <li>context = "run" : associa cada run ao seu plan, usando {@code plan_id};</li>
     *   <li>outros contextos: ignorados.</li>
     * </ul>
     *
     * @param entities        JSONArray contendo runs ou plans.
     * @param releaseMetaById Mapa de metadados de release (não utilizado aqui, mantido por compatibilidade).
     * @param context         Tipo de entidade ("plan" ou "run").
     */
    public void enrich(JSONArray entities,
                       Map<String, JSONObject> releaseMetaById,
                       String context) {

        if (entities == null || context == null) {
            return;
        }

        if ("plan".equalsIgnoreCase(context)) {
            enrichPlans(entities);
        } else if ("run".equalsIgnoreCase(context)) {
            enrichRuns(entities);
        }
        // Outros contextos são ignorados intencionalmente.
    }

    /**
     * Indexa os plans por ID e garante que cada plan possua um array "runs".
     */
    private void enrichPlans(JSONArray plans) {
        plansById.clear();

        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) {
                continue;
            }

            int id = plan.optInt("id", -1);
            if (id <= 0) {
                continue;
            }

            // Garante a existência do array "runs" dentro do plan
            JSONArray runsArray = plan.optJSONArray("runs");
            if (runsArray == null) {
                runsArray = new JSONArray();
                plan.put("runs", runsArray);
            }

            plansById.put(id, plan);
        }
    }

    /**
     * Associa cada run ao seu plan, usando o campo "plan_id".
     */
    private void enrichRuns(JSONArray runs) {
        if (plansById.isEmpty()) {
            // Se não houver plans indexados, não há como associar.
            return;
        }

        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run == null) {
                continue;
            }

            int planId = run.optInt("plan_id", -1);
            if (planId <= 0) {
                // Runs sem plan_id não são associadas.
                continue;
            }

            JSONObject plan = plansById.get(planId);
            if (plan == null) {
                // plan_id não encontrado entre os plans carregados.
                continue;
            }

            JSONArray planRuns = plan.optJSONArray("runs");
            if (planRuns == null) {
                planRuns = new JSONArray();
                plan.put("runs", planRuns);
            }

            planRuns.put(run);
        }
    }
}
