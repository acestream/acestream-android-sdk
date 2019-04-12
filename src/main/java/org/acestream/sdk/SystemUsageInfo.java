package org.acestream.sdk;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SystemUsageInfo {
    private final static String TAG = "AceStream/SUI";

    public double memoryTotal;
    public double memoryAvailable;
    public float cpuUsage;

    public SystemUsageInfo() {
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();

            root.put("memoryTotal", memoryTotal);
            root.put("memoryAvailable", memoryAvailable);
            root.put("cpuUsage", cpuUsage);

            return root.toString();
        }
        catch(JSONException e) {
            Log.e(TAG, "failed to serialize system usage info", e);
            return null;
        }
    }

    public static SystemUsageInfo fromJson(String data) {
        try {
            SystemUsageInfo systemUsageInfo = new SystemUsageInfo();

            JSONObject root = new JSONObject(data);

            systemUsageInfo.memoryTotal = root.getDouble("memoryTotal");
            systemUsageInfo.memoryAvailable = root.getDouble("memoryAvailable");
            systemUsageInfo.cpuUsage= (float)root.getDouble("cpuUsage");

            return systemUsageInfo;
        }
        catch(JSONException e) {
            Log.e(TAG, "failed to deserialize system usage info", e);
            return null;
        }
    }
}
