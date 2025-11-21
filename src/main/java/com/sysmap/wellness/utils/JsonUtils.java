package com.sysmap.wellness.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitário centralizado para manipulação de JSON (org.json) dentro do projeto.
 *
 * <p>O objetivo é eliminar duplicações de código espalhadas em diferentes serviços
 * (por exemplo, utilitários locais em KPIs, ReleaseUtils, etc.), garantindo um único
 * ponto de manutenção.</p>
 */
public final class JsonUtils {

    private JsonUtils() {
        // Utilitário estático, não deve ser instanciado.
    }

    /**
     * Realiza um "deep clone" de um {@link JSONObject} usando serialização para string.
     *
     * @param original objeto a ser clonado.
     * @return novo objeto JSON com o mesmo conteúdo ou {@code null} se original for nulo.
     */
    public static JSONObject deepClone(JSONObject original) {
        if (original == null) {
            return null;
        }
        return new JSONObject(original.toString());
    }

    /**
     * Realiza um "deep clone" de um {@link JSONArray}.
     *
     * @param original array a ser clonado.
     * @return novo array JSON com o mesmo conteúdo ou {@code null} se original for nulo.
     */
    public static JSONArray deepClone(JSONArray original) {
        if (original == null) {
            return null;
        }
        return new JSONArray(original.toString());
    }

    /**
     * Obtém um {@link JSONArray} de forma segura. Se o campo não existir ou não for um array,
     * retorna um array vazio.
     *
     * @param json objeto JSON de origem.
     * @param field nome do campo.
     * @return {@link JSONArray} nunca nulo.
     */
    public static JSONArray getArray(JSONObject json, String field) {
        if (json == null || field == null) {
            return new JSONArray();
        }
        JSONArray array = json.optJSONArray(field);
        return array != null ? array : new JSONArray();
    }

    /**
     * Converte um {@link JSONArray} de objetos JSON em uma lista de {@link JSONObject}.
     *
     * @param array array de entrada.
     * @return lista de {@link JSONObject}, nunca nula.
     */
    public static List<JSONObject> toObjectList(JSONArray array) {
        List<JSONObject> result = new ArrayList<>();
        if (array == null) {
            return result;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) {
                result.add(obj);
            }
        }

        return result;
    }

    /**
     * Mescla o conteúdo de vários {@link JSONArray} em um único array.
     *
     * @param arrays arrays de entrada.
     * @return array resultante contendo todos os elementos.
     */
    public static JSONArray merge(JSONArray... arrays) {
        JSONArray merged = new JSONArray();
        if (arrays == null) {
            return merged;
        }

        for (JSONArray array : arrays) {
            if (array == null) {
                continue;
            }
            for (int i = 0; i < array.length(); i++) {
                merged.put(array.get(i));
            }
        }

        return merged;
    }

    /**
     * Obtém uma string de um JSON, retornando string vazia caso o campo não exista.
     *
     * @param json objeto JSON.
     * @param field campo desejado.
     * @return valor do campo ou string vazia.
     */
    public static String safeString(JSONObject json, String field) {
        if (json == null || field == null) {
            return "";
        }
        return json.optString(field, "");
    }

    /**
     * Obtém um inteiro de um JSON, retornando 0 caso ocorra qualquer problema.
     *
     * @param json objeto JSON.
     * @param field campo desejado.
     * @return valor inteiro, nunca negativo.
     */
    public static int safeInt(JSONObject json, String field) {
        if (json == null || field == null) {
            return 0;
        }
        try {
            int value = json.optInt(field, 0);
            return Math.max(0, value);
        } catch (JSONException e) {
            LoggerUtils.warn("JsonUtils.safeInt - erro ao ler campo '" + field + "': " + e.getMessage());
            return 0;
        }
    }
}
