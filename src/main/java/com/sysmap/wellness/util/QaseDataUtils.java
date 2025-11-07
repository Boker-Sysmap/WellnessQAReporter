package com.sysmap.wellness.util;

import org.json.JSONArray;
import org.json.JSONObject;

public class QaseDataUtils {

    public static JSONArray getCases(JSONObject projectData) {
        return projectData.optJSONArray("case");
    }

    public static JSONArray getResults(JSONObject projectData) {
        return projectData.optJSONArray("result");
    }

    public static JSONArray getDefects(JSONObject projectData) {
        return projectData.optJSONArray("defect");
    }

    public static JSONArray getUsers(JSONObject projectData) {
        return projectData.optJSONArray("user");
    }

    public static JSONArray getRuns(JSONObject projectData) {
        return projectData.optJSONArray("run");
    }
}
