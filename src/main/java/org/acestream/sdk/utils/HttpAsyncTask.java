package org.acestream.sdk.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.acestream.sdk.interfaces.IHttpAsyncTaskListener;

@SuppressLint("UseSparseArrays")
public class HttpAsyncTask extends AsyncTask<String, Void, String> {

	private final static String TAG = "AceStream/HttpAsyncTask";
	
	public static final int HTTPTASK_GET_SETTINGS = 0;
	public static final int HTTPTASK_GET_PROFILE = 1;
	public static final int HTTPTASK_SET_SETTINGS = 2;
	public static final int HTTPTASK_GET_EXTENSIONS = 3;
	public static final int HTTPTASK_ADD_EXTENSION = 4;
	public static final int HTTPTASK_CLEAN_CACHE = 5;
	public static final int HTTPTASK_SHUTDOWN = 6;
	public static final int HTTPTASK_SET_IS_ONLINE = 7;
	public static final int HTTPTASK_GET_MEDIA_FILES = 8;
	public static final int HTTPTASK_START_CONTENT = 9;
	public static final int HTTPTASK_GET_DOWNLOAD_STATUS = 10;
	public static final int HTTPTASK_STOP_DOWNLOAD = 11;
	public static final int HTTPTASK_SET_SCREEN_STATUS = 12;
	public static final int HTTPTASK_OPEN_IN_PLAYER = 16;
	public static final int HTTPTASK_TEST_SEGMENTER_DIRECT = 17;
	public static final int HTTPTASK_TEST_SEGMENTER_OUTPUT_CONTEXT = 18;
	public static final int HTTPTASK_LIVE_SEEK = 19;
	public static final int HTTPTASK_SET_STREAM = 20;

    private static final String HOST = "127.0.0.1";
	private static final Map<Integer, String> URLS;
	
	static {
		Map<Integer, String> map = new HashMap<>();
		map.put(HTTPTASK_GET_SETTINGS, "/webui/app/%token%/settings/get");
		map.put(HTTPTASK_GET_PROFILE, "/webui/app/%token%/settings/get");
		map.put(HTTPTASK_SET_SETTINGS, "/webui/app/%token%/settings/set");
		map.put(HTTPTASK_GET_EXTENSIONS, "/webui/app/%token%/services/get");
		map.put(HTTPTASK_ADD_EXTENSION, "/webui/app/%token%/cmd/load-extension");
		map.put(HTTPTASK_CLEAN_CACHE, "/webui/app/%token%/cmd/clearcache");
		map.put(HTTPTASK_SHUTDOWN, "/webui/app/%token%/cmd/shutdown");
		map.put(HTTPTASK_SET_IS_ONLINE, "/webui/app/%token%/cmd/isonline");
		map.put(HTTPTASK_SET_SCREEN_STATUS, "/webui/app/%token%/cmd/screenstatus");
		map.put(HTTPTASK_TEST_SEGMENTER_DIRECT, "/webui/app/%token%/cmd/test-segmenter-direct");
		map.put(HTTPTASK_TEST_SEGMENTER_OUTPUT_CONTEXT, "/webui/app/%token%/cmd/test-segmenter-output-context");
        URLS = Collections.unmodifiableMap(map);
    }

	private int mHttpApiPort;
	private String mAccessToken;
	private int mTaskType;
	private String mTargetUrl;
	private Map<String, Object> mExtraData;
	private WeakReference<IHttpAsyncTaskListener> mListenerRef = null;

	public static final class Factory {
		private int mHttpApiPort;
		private String mAccessToken;

		public Factory(int httpApiPort, String accessToken) {
			mHttpApiPort = httpApiPort;
			mAccessToken = accessToken;
		}

		public int getHttpApiPort() {
			return mHttpApiPort;
		}

		public String getAccessToken() {
			return mAccessToken;
		}

		public HttpAsyncTask build(int type) {
			return new HttpAsyncTask.Builder(mHttpApiPort, mAccessToken, type)
					.build();
		}

		public HttpAsyncTask build(int type, IHttpAsyncTaskListener listener) {
			return new HttpAsyncTask.Builder(mHttpApiPort, mAccessToken, type)
					.setListener(listener)
					.build();
		}

		public HttpAsyncTask build(int type, IHttpAsyncTaskListener listener, String targetUrl) {
			return new HttpAsyncTask.Builder(mHttpApiPort, mAccessToken, type)
					.setListener(listener)
					.setTargetUrl(targetUrl)
					.build();
		}

		public HttpAsyncTask build(int type, IHttpAsyncTaskListener listener, String targetUrl, Map<String, Object> extraData) {
			return new HttpAsyncTask.Builder(mHttpApiPort, mAccessToken, type)
					.setListener(listener)
					.setTargetUrl(targetUrl)
					.setExtraData(extraData)
					.build();
		}
	}

	public static final class Builder {
		private HttpAsyncTask mTask;

		public Builder(int httpApiPort, String accessToken, int type) {
			mTask = new HttpAsyncTask(httpApiPort, accessToken, type);
		}

		public Builder setListener(IHttpAsyncTaskListener listener) {
			mTask.mListenerRef = new WeakReference<>(listener);
			return this;
		}

		public Builder setTargetUrl(String targetUrl) {
			mTask.mTargetUrl = targetUrl;
			return this;
		}

		public Builder setExtraData(Map<String, Object> extraData) {
			mTask.mExtraData = extraData;
			return this;
		}

		public HttpAsyncTask build() {
			return mTask;
		}
	}

	public HttpAsyncTask(int httpApiPort, String accessToken, int type) {
		this(httpApiPort, accessToken, type, null, null, null);
	}
	
	public HttpAsyncTask(int httpApiPort, String accessToken, int type, IHttpAsyncTaskListener listener) {
	    this(httpApiPort, accessToken, type, listener, null, null);
	}
	
	public HttpAsyncTask(int httpApiPort, String accessToken, int type, IHttpAsyncTaskListener listener, String targetUrl) {
	    this(httpApiPort, accessToken, type, listener, targetUrl, null);
	}
	
	public HttpAsyncTask(int httpApiPort, String accessToken, int type, IHttpAsyncTaskListener listener, String targetUrl, Map<String, Object> extraData) {
	    //Log.v(TAG, "created: type=" + type + " targetUrl=" + targetUrl);
		mHttpApiPort = httpApiPort;
		mAccessToken = accessToken;
		mTaskType = type;
		mTargetUrl = targetUrl;
		mExtraData = extraData;
		mListenerRef = new WeakReference<>(listener);
	}
	
	public void execute2(String... params) {
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
	        super.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
	    }
	    else {
	        super.execute(params);
	    }
	}
	
	@Override
	protected void onPreExecute() {
		if(mListenerRef.get() != null) {
		    try {
		        mListenerRef.get().onHttpAsyncTaskStart(mTaskType);
		    }
		    catch(Exception e) {
		        Log.e(TAG, "onPreExecute: error", e);
		    }
		}
	}
	
	@Override
	protected String doInBackground(String... params) {
        String strUrl;

        String token;
        if(mAccessToken != null) {
        	token = mAccessToken;
		}
		else {
        	// need string to avoid errors
        	token = "";
		}

        if(mTargetUrl == null) {
            strUrl = URLS.get(mTaskType);
            //Log.v(TAG, "doInBackground: use predefined url: type=" + String.valueOf(mTaskType) + " url=" + strUrl);
            if(strUrl == null) {
                return null;
            }
            strUrl = strUrl.replace("%token%", token);
        }
        else {
            //Log.v(TAG, "doInBackground: use target url: " + mTargetUrl);
            strUrl = mTargetUrl;
        }
        
        String response = "";
        String strMethod = params[0];
        String strParams = params.length > 1 ? params[1] : "";
        String strContentType = params.length > 2 ? params[2] : "";

        URL url;
	    HttpURLConnection connection = null;
	    BufferedReader reader;
	    BufferedWriter writer;
	    try {
	    	if( !strParams.equalsIgnoreCase("") && strMethod.equalsIgnoreCase("GET") ) {
	    		strUrl += "?" + strParams;
	    	}

	    	url = new URL("http", HOST, mHttpApiPort, strUrl);
	    	
	    	if(!url.toString().contains("password=")) {
	    	    //Log.v(TAG, url.toString().replace(token, "webui"));
	    	}
	    	
	    	connection = (HttpURLConnection) url.openConnection();
	    	connection.setRequestMethod(strMethod);
	        connection.setConnectTimeout(10000);
	        connection.setDoInput(true);
	        
	        if( !strParams.equalsIgnoreCase("") && strMethod.equalsIgnoreCase("POST") ) {
	        	connection.setDoOutput(true);
        		connection.setRequestProperty("Content-Length", Integer.toString(strParams.getBytes().length));
        		if(!strContentType.equalsIgnoreCase("")) {
        			connection.setRequestProperty("Content-Type", strContentType);
        		}

        		writer = new BufferedWriter(new OutputStreamWriter( connection.getOutputStream() ));
        		writer.write(strParams);
	        	writer.flush();
	        	writer.close();
	        }

	        reader = new BufferedReader(new InputStreamReader( connection.getInputStream() ));
	        
	        StringBuilder builder = new StringBuilder();
	        String buffer;
	        while((buffer = reader.readLine()) != null) {
	            builder.append(buffer);
	        }

	        reader.close();
	        response = builder.toString();
	    } catch(Exception e) {
	    	Log.e(TAG, "got exception: " + e.getMessage());
            response = "{\"response\": null, \"error\": \"HttpURLConnection error\"}";
        } finally {
        	if(connection != null)
	        	connection.disconnect();
        }
        return response;
	}

	@Override 
    protected void onPostExecute(String result) {
		if(mListenerRef.get() != null) {
		    try {
		        mListenerRef.get().onHttpAsyncTaskFinish(mTaskType, result, mExtraData);
			}
			catch(Exception e) {
		        Log.e(TAG, "onPreExecute: error", e);
		    }
		}
    }
	
}
