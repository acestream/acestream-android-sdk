package org.acestream.sdk.utils;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

public class HttpRequest {
	private final static String TAG = "AceStream/HttpRequest";

	public interface Callback {
		void onResponse(Response response);
	}

	public static void get(Uri uri, Map<String,String> headers, Callback callback) {
		HttpRequestTask task = new HttpRequestTask("GET", uri, headers, callback);
		task.execute2();
	}

	public static class Response {
		public Map<String,List<String>> headers;
		public String body;
	}

	private static class HttpRequestTask extends AsyncTask<Void, Void, Response> {
		private String mMethod;
		private Uri mUri;
		private Map<String,String> mHeaders;
		private Callback mCallback;

		HttpRequestTask(String method, Uri uri, Map<String,String> headers, Callback callback) {
			mMethod = method;
			mUri = uri;
			mCallback = callback;
			mHeaders = headers;
		}

		public void execute2() {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				super.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			else {
				super.execute();
			}
		}

		@Override
		protected Response doInBackground(Void... params) {
			Response response;
			URL url;
			URLConnection connection = null;
			BufferedReader reader;
			BufferedWriter writer;
			try {
				url = new URL(mUri.toString());

				connection = url.openConnection();
				//connection.setRequestMethod(strMethod);
				connection.setConnectTimeout(10000);
				if(mHeaders != null) {
				    for(Map.Entry<String,String> item: mHeaders.entrySet()) {
                        connection.setRequestProperty(item.getKey(), item.getValue());
                    }
                }
				connection.setDoInput(true);

//				if( !strParams.equalsIgnoreCase("") && strMethod.equalsIgnoreCase("POST") ) {
//					connection.setDoOutput(true);
//					connection.setRequestProperty("Content-Length", Integer.toString(strParams.getBytes().length));
//					if(!strContentType.equalsIgnoreCase("")) {
//						connection.setRequestProperty("Content-Type", strContentType);
//					}
//
//					writer = new BufferedWriter(new OutputStreamWriter( connection.getOutputStream() ));
//					writer.write(strParams);
//					writer.flush();
//					writer.close();
//				}

				reader = new BufferedReader(new InputStreamReader( connection.getInputStream() ));

				StringBuilder builder = new StringBuilder();
				String buffer;
				while((buffer = reader.readLine()) != null) {
					builder.append(buffer);
				}

				reader.close();

				response = new Response();
				response.body = builder.toString();
				response.headers = connection.getHeaderFields();
			} catch(Exception e) {
				Log.e(TAG, "got exception: " + e.getMessage());
				response = null;
			} finally {
				//if(connection != null) {
				//	connection.disconnect();
				//}
			}
			return response;
		}

		@Override
		protected void onPostExecute(Response response) {
			mCallback.onResponse(response);
		}
	}
}

