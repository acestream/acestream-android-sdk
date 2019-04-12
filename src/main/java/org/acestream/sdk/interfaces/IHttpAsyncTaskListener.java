package org.acestream.sdk.interfaces;

import java.util.Map;

public interface IHttpAsyncTaskListener {

	public void onHttpAsyncTaskStart(int type);
	public void onHttpAsyncTaskFinish(int type, String result, Map<String, Object> extraData);
	
}
