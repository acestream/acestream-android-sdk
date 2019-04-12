package org.acestream.sdk;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JsonRpcMessage {
	private final static String TAG = "AceStream/JRM";

	private String mMethod;
	private Map<String,Object> mParams;

	public JsonRpcMessage(String method) {
		mMethod = method;
		mParams = new HashMap<>();
	}

	public JsonRpcMessage(String method, String paramName, Object paramValue) {
		this(method);
		addParam(paramName, paramValue);
	}

	public String getMethod() {
		return mMethod;
	}

	public Map<String, Object> getParams() {
		return mParams;
	}

	public String getString(String name) {
		return getString(name, null);
	}

	public String getString(String name, String defaultValue) {
		Object value = getParam(name);
		if(value == null) {
			return defaultValue;
		}
		return String.valueOf(value);
	}

	public Long getLong(String name) {
		return getLong(name, 0);
	}

	public Long getLong(String name, long defaultValue) {
		Object value = getParam(name);
		if(value == null) {
			return defaultValue;
		}
		return Long.valueOf(value.toString());
	}

	public int getInt(String name) {
		return getInt(name, 0);
	}

	public int getInt(String name, int defaultValue) {
		Object value = getParam(name);
		if(value == null) {
			return defaultValue;
		}

		try {
			return Integer.valueOf(value.toString());
		}
		catch(Throwable e) {
			Log.e(TAG, "getInt() failed: value=" + value, e);
			return 0;
		}
	}

	public float getFloat(String name) {
		Object value = getParam(name);
		if(value == null) {
			return 0f;
		}
		return Float.valueOf(value.toString());
	}

	public boolean getBoolean(String name) {
		return getBoolean(name, false);
	}

	public boolean getBoolean(String name, boolean defaultValue) {
		Object value = getParam(name);
		if(value == null) {
			return defaultValue;
		}
		return Boolean.valueOf(value.toString());
	}

	public JSONArray getJSONArray(String name) {
		Object value = getParam(name);
		if(value == null) {
			return null;
		}
		return (JSONArray)value;
	}

	public Object getParam(String name) {
		return mParams.get(name);
	}

	public void addParam(String name, Object value) {
		mParams.put(name, value);
	}

	public String asString() throws JSONException {
		JSONObject root = new JSONObject();
		root.put("jsonrpc", "2.0");
		root.put("method", mMethod);

		if(!mParams.isEmpty()) {
			JSONObject paramsObj = new JSONObject();
			for (String key : mParams.keySet()) {
				paramsObj.put(key, mParams.get(key));
			}
			root.put("params", paramsObj);
		}
		return root.toString();
	}

	public String toString() {
		try {
			return asString();
		}
		catch(JSONException e) {
			return null;
		}
	}

	public static JsonRpcMessage fromString(String message) throws JSONException {
		if(TextUtils.isEmpty(message)) {
			throw new JSONException("empty message");
		}

		JSONObject root = new JSONObject(message);

		if(!root.has("method")) {
			throw new JSONException("missing method");
		}
		JsonRpcMessage msg = new JsonRpcMessage(root.getString("method"));

		if(root.has("params")) {
			JSONObject params = root.getJSONObject("params");
			Iterator<String> keys = params.keys();
			while(keys.hasNext()) {
				String key = keys.next();
				msg.addParam(key, params.get(key));
			}
		}

		return msg;
	}
}
