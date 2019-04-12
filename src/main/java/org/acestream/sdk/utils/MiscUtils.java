package org.acestream.sdk.utils;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.client.BuildConfig;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.SystemUsageInfo;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.NonNull;

public class MiscUtils {

	private final static String TAG = "AceStream/Util";

	public static boolean deleteDir(File dir) {
		if(dir.isDirectory()) {
			String[] children = dir.list();
			for(int i=0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
	            if(!success) {
	            	return false;
	            }
			}
		}
		return dir.delete();
	}
	
	public static int chmod(File path, int mode) throws Exception {
		Class<?> fileUtils = Class.forName("android.os.FileUtils");
		Method setPermissions =	fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
		return (Integer)setPermissions.invoke(null, path.getAbsolutePath(), mode, -1, -1);
	}
	
	public static String hash(InputStream inputStream) throws NoSuchAlgorithmException, IOException {
		final MessageDigest md = MessageDigest.getInstance("SHA1");
		inputStream.reset();
		
		byte[] dataBytes = new byte[1024];
	    int nread = 0; 
	    while ((nread = inputStream.read(dataBytes)) != -1) {
	    	md.update(dataBytes, 0, nread);
	    };
	    
	    byte[] mdbytes = md.digest();
	    StringBuffer sb = new StringBuffer("");
	    for (int i = 0; i < mdbytes.length; i++) {
	    	sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
	    }
		return sb.toString();
	}

	public static String sha1Hash(byte[] bytes) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		String hash = null;
		MessageDigest digest = MessageDigest.getInstance( "SHA-1" );
		digest.update(bytes, 0, bytes.length);
		bytes = digest.digest();

		// This is ~55x faster than looping and String.formating()
		hash = bytesToHex( bytes );

		return hash;
	}

	public static String sha256Hash(String toHash) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		return sha256Hash(toHash.getBytes("UTF-8"));
	}

	public static String sha256Hash(byte[] bytes) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		String hash = null;
		MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
		digest.update(bytes, 0, bytes.length);
		bytes = digest.digest();

		// This is ~55x faster than looping and String.formating()
		hash = bytesToHex( bytes );

		return hash;
	}

	public static String sha1Hash(String toHash) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		if(toHash == null) {
			return null;
		}
		return sha1Hash(toHash.getBytes("UTF-8"));
	}

	// http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex( byte[] bytes )
	{
		char[] hexChars = new char[ bytes.length * 2 ];
		for( int j = 0; j < bytes.length; j++ )
		{
			int v = bytes[ j ] & 0xFF;
			hexChars[ j * 2 ] = hexArray[ v >>> 4 ];
			hexChars[ j * 2 + 1 ] = hexArray[ v & 0x0F ];
		}
		return new String( hexChars );
	}

	public static String durationStringFromMilliseconds(long milliseconds) {

		long hours = 0;
		long minutes = 0;
		long seconds = 0;

		if (milliseconds >= (1000 * 60 * 60)) {
			hours = milliseconds / (1000 * 60 * 60);
		}
		if (milliseconds >= (1000 * 60)) {
			minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
		}
		if (milliseconds >= 1000) {
			seconds = ((milliseconds % (1000 * 60 * 60)) % (1000 * 60)) / 1000;
		}

		String runtime = "";

		if (hours > 0) {
			runtime = String.valueOf(hours) + ":";
		}
		if (minutes > 0) {
			if (minutes < 10 && hours > 0) {
				runtime += "0";
			}
			runtime += String.valueOf(minutes) + ":";
		} else {
			if (hours > 0) {
				runtime += "00:";
			}
		}

		if (seconds > 0) {
			if (seconds < 10) {
				runtime += "0";
			}
			runtime += String.valueOf(seconds);
			if (hours < 1 && minutes < 1) {
				runtime = "0:" + runtime;
			}
		} else {
			if (hours < 1 && minutes < 1) {
				runtime += "0:00";
			} else {
				runtime += "00";
			}
		}

		return runtime;
	}

	public static String formatInterval(long millis) {
		return String.valueOf(millis / 1000) + "s";
	}

    public static String ifNull(String string, String defaultValue) {
		return string == null ? defaultValue : string;
	}

	public static String getRequiredStringExtra(Intent intent, String extraName) {
		String value = intent.getStringExtra(extraName);
		if(value == null) {
			throw new IllegalStateException("missing required extra " + extraName);
		}
		return value;
	}

	public static boolean getRequiredBooleanExtra(Intent intent, String extraName) {
		if(!intent.hasExtra(extraName)) {
			throw new IllegalStateException("missing required extra " + extraName);
		}
		return intent.getBooleanExtra(extraName, false);
	}

	public static int getRequiredIntExtra(Intent intent, String extraName) {
		if(!intent.hasExtra(extraName)) {
			throw new IllegalStateException("missing required extra " + extraName);
		}
		return intent.getIntExtra(extraName, 0);
	}

	public static int getIntQueryParameter(@NonNull Uri uri, @NonNull String key, int defaultValue) {
		try {
			return Integer.parseInt(MiscUtils.getQueryParameter(uri, key));
		}
		catch(NumberFormatException|UnsupportedEncodingException e) {
			return defaultValue;
		}
	}

	public static String getQueryParameter(Uri uri, String key) throws UnsupportedEncodingException {
		Map<String,String> params = getQueryParameters(uri);
		return params.get(key);
	}

	public static Uri removeQueryParameter(Uri uri, String key) throws UnsupportedEncodingException {
		Uri.Builder builder = new Uri.Builder();
		builder.scheme(uri.getScheme());
		builder.authority(uri.getAuthority());
		builder.path(uri.getPath());

		for(Map.Entry<String, String> item: getQueryParameters(uri).entrySet()) {
			if(!TextUtils.equals(item.getKey(), key)) {
				builder.appendQueryParameter(item.getKey(), item.getValue());
			}
		}


		return builder.build();
	}

	public static Map<String,String> getQueryParameters(Uri uri) throws UnsupportedEncodingException {
		if(uri == null) {
			return Collections.emptyMap();
		}

		// Don't use uri.getQuery(), because it urldecodes all query string and makes unable to
		// parse such uri:
		// acestream:?data=%2Fpath%2Fto%2Ftest%26file&index=0
		// getQuery() returns:
		// data=/path/to/test&file&index=0
		// Cannot split by & such string.
		String[] urlParts = uri.toString().split("\\?");
		if(urlParts.length < 2) {
			return Collections.emptyMap();
		}

		String query = urlParts[1];

		Map<String,String> params = new HashMap<>();
		int start = 0;
		do {
			int next = query.indexOf('&', start);
			int end = (next == -1) ? query.length() : next;

			int separator = query.indexOf('=', start);
			if (separator > end || separator == -1) {
				separator = end;
			}

			String name = query.substring(start, separator);
			String value = URLDecoder.decode(query.substring(separator+1, end), "UTF-8");
			params.put(name, value);

			// Move start to end of name.
			start = end + 1;
		} while (start < query.length());

		return Collections.unmodifiableMap(params);
	}

	public static boolean getJsonBoolean(JSONObject obj, String name, boolean defaultValue) {
		if(!obj.has(name)) {
			return defaultValue;
		}
		try {
			return obj.getInt(name) != 0;
		}
		catch(JSONException e1) {
			try {
				return obj.getBoolean(name);
			}
			catch(JSONException e2) {
				return defaultValue;
			}
		}
	}

	public static File getFile(String path) {
		if(path == null)
			return null;

		if(path.startsWith("file:"))
			return getFile(Uri.parse(path));
		else
			return new File(path);
	}

	public static File getFile(Uri uri) {
		if(uri == null)
			return null;

		String path = null;
		if(TextUtils.equals(uri.getScheme(), "file")) {
			path = uri.getPath();
		}

		if(path == null)
			return null;

		return new File(path);
	}

	public static String dump(List<Integer> value) {
		int[] array = new int[value.size()];
		for(int i=0; i < array.length; i++) {
			array[i] = value.get(i);
		}
		return dump(array);
	}

	public static String dump(int[] value) {
		if(value == null) {
			return null;
		}
		else {
			String[] strings = new String[value.length];
			for(int i=0; i < value.length; i++) {
				strings[i] = String.valueOf(value[i]);
			}
			return TextUtils.join(",", strings);
		}
	}

	public static String dump(String[] value) {
		if(value == null) {
			return null;
		}
		else {
			return TextUtils.join(",", value);
		}
	}

	public static boolean isNetworkConnected(Context context) {
		boolean connected = false;

		ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if(conn != null) {
			NetworkInfo networkInfo = conn.getActiveNetworkInfo();
			if(networkInfo != null) {
				connected = networkInfo.isConnected();
			}
		}

		return connected;
	}

	public static boolean isConnectedToMobileNetwork(Context context) {
		if(AceStream.isAndroidTv()) {
			return false;
		}
		else {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm == null) {
				return false;
			}
			else {
				NetworkInfo ni = cm.getActiveNetworkInfo();
				if (ni != null && ni.isConnectedOrConnecting()) {
					return isMobileNetwork(ni);
				} else {
					return false;
				}
			}
		}
	}

	private static boolean isWifiNetwork(NetworkInfo ni) {
		return ni.getType() == ConnectivityManager.TYPE_WIFI;
	}

	private static boolean isWiMaxNetwork(NetworkInfo ni) {
		return ni.getType() == ConnectivityManager.TYPE_WIMAX;
	}

	private static boolean isEthernetNetwork(NetworkInfo ni) {
		return ni.getType() == ConnectivityManager.TYPE_ETHERNET;
	}

	public static boolean isMobileNetwork(NetworkInfo ni) {
		return !isWifiNetwork(ni) && !isEthernetNetwork(ni) && !isWiMaxNetwork(ni) && ni.getTypeName().equalsIgnoreCase("MOBILE");
	}

	public static SystemUsageInfo getSystemUsage(Context context) {
		ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
		ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		activityManager.getMemoryInfo(mi);
		double totalMegs = mi.totalMem / 0x100000L;
		double availableMegs = mi.availMem / 0x100000L;

		//Percentage can be calculated for API 16+
		double percentAvail = mi.availMem / (double) mi.totalMem * 100.0;

		float cpuUsage = getCpuUsage();

		SystemUsageInfo info = new SystemUsageInfo();
		info.memoryTotal = totalMegs;
		info.memoryAvailable = availableMegs;
		info.cpuUsage = cpuUsage;

		return info;
	}

	public static float getCpuUsage() {
		try {
			// we don't have access to /proc/stat on Android 8+
			if(Build.VERSION.SDK_INT >= 26) {
				return 0;
			}

			RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
			String load = reader.readLine();

			String[] toks = load.split(" ");

			long idle1 = Long.parseLong(toks[5]);
			long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
					+ Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

			try {
				Thread.sleep(360);
			} catch (Exception e) {}

			reader.seek(0);
			load = reader.readLine();
			reader.close();

			toks = load.split(" ");

			long idle2 = Long.parseLong(toks[5]);
			long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
					+ Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

			return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

		} catch (IOException ex) {
			// pass
		}

		return 0;
	}

	public static String getMyIp(Context context) {
		try {
			InetAddress addr = NetworkUtils.getIpAddress(context);
			if(addr == null) {
				Log.e(TAG, "Cannot get my ip address");
				return null;
			}
			else {
				return addr.getHostAddress();
			}
		}
		catch(UnknownHostException e) {
			Log.e(TAG, "getMyIp: error", e);
			return null;
		}
	}

	public static int getFileIndex(Uri uri) {
		if(uri == null) {
			return 0;
		}

		int index;
		try {
			index = Integer.parseInt(MiscUtils.getQueryParameter(uri, "index"));
		}
		catch(NumberFormatException | UnsupportedEncodingException e) {
			index = 0;
		}
		return index;
	}

	public static int getIntFromStringPreference(SharedPreferences prefs, String key, int defaultValue) {
		String value = prefs.getString(key, null);
		if(value == null) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(value);
		}
		catch(NumberFormatException e) {
			return defaultValue;
		}
	}

	public static String getRendererIp(String sout) {
		Pattern p = Pattern.compile("chromecast\\{ip=(\\d+\\.\\d+\\.\\d+\\.\\d+)");
		Matcher m = p.matcher(sout);
		if(m.find()) {
			return m.group(1);
		}
		else {
			return null;
		}
	}

	public static List<ResolveInfo> resolveActivityIntent(Context ctx, Intent intent) {
		PackageManager pm = ctx.getPackageManager();
		List<ResolveInfo> resolveInfo = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		if(resolveInfo == null || resolveInfo.size() == 0) {
			return null;
		}

		return resolveInfo;
	}

	public static List<ResolveInfo> resolveServiceIntent(Context ctx, Intent intent) {
		PackageManager pm = ctx.getPackageManager();
		List<ResolveInfo> resolveInfo = pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);
		if(resolveInfo == null || resolveInfo.size() == 0) {
			return null;
		}

		return resolveInfo;
	}

	public static List<ResolveInfo> resolveBroadcastIntent(Context ctx, Intent intent) {
		PackageManager pm = ctx.getPackageManager();
		List<ResolveInfo> resolveInfo = pm.queryBroadcastReceivers(intent, PackageManager.MATCH_DEFAULT_ONLY);
		if(resolveInfo == null || resolveInfo.size() == 0) {
			return null;
		}

		return resolveInfo;
	}

	public static int getAppVersionCode(Context ctx, String packageName) {
		int versionCode;
		try {
			PackageInfo pkgInfo = ctx.getPackageManager().getPackageInfo(packageName, 0);
			versionCode = pkgInfo.versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			versionCode = -1;
		}
		return versionCode;
	}

	public static String getAppVersionName(Context ctx, String packageName) {
		String versionName;
		try {
			PackageInfo pkgInfo = ctx.getPackageManager().getPackageInfo(packageName, 0);
			versionName = pkgInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			versionName = null;
		}
		return versionName;
	}

	public static String getIntegerValue(String s) {
		int intVal;
		try {
			intVal = Integer.parseInt(s);
		}
		catch(Exception e) {
			intVal = 0;
		}

		return String.valueOf(intVal);
	}

	public static long bytesToMegabytes(long bytes) {
		long mb = bytes / 1024 / 1024;
		if ((bytes % (1024 * 1024)) > 0) {
			mb++;
		}
		return mb;
	}

	public static String parseExtension(String path) {
		if(path == null) return "";
		int pos = path.lastIndexOf(".");
		if(pos == -1) return "";
		return path.substring(pos);
	}

	public static byte[] readBytesFromFile(@NonNull String path) throws IOException {
		return readBytesFromStream(new FileInputStream(path));
	}

	public static byte[] readBytesFromContentUri(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
		try {
			return readBytesFromStream(resolver.openInputStream(uri));
		}
		catch(SecurityException e) {
			throw new IOException(e);
		}
	}

	public static byte[] readBytesFromStream(@NonNull InputStream stream) throws IOException {
		byte[] buffer = new byte[stream.available()];
		stream.read(buffer);
		stream.close();
		return buffer;
	}

	public static int randomIntRange(int from, int to) {
	    return from + (new Random().nextInt(to - from));
    }

	@SuppressWarnings("OctalInteger")
	public static void unzip(String input, String destination, boolean replace, boolean deleteInput) throws Exception {
		final int BUFFER_SIZE = 4096;
		BufferedOutputStream bufferedOutputStream;

		File destDir = new File(destination);
		if(!destDir.exists()) {
			destDir.mkdir();
		}

		File inputFile = new File(input);
		ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
		ZipEntry zipEntry;
		while((zipEntry = zipInputStream.getNextEntry()) != null) {
			String zipEntryName = zipEntry.getName();

			File fRem = new File(destination, zipEntryName);
			if(fRem.exists()) {
				if(replace) {
					boolean b = MiscUtils.deleteDir(fRem);
					if(!b) {
						Log.e(TAG, "Unpack failed to delete " + destination + zipEntryName);
					}
				}
			}

			File fUnzip = new File(destination, zipEntryName);
			if(!fUnzip.exists()) {
				if(zipEntry.isDirectory()) {
					fUnzip.mkdirs();
					MiscUtils.chmod(fUnzip, 0755);
				}
				else {
					if(!fUnzip.getParentFile().exists()) {
						fUnzip.getParentFile().mkdirs();
						MiscUtils.chmod(fUnzip.getParentFile(), 0755);
					}

					byte buffer[] = new byte[BUFFER_SIZE];
					bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(fUnzip), BUFFER_SIZE);
					int count;

					while( (count = zipInputStream.read(buffer, 0, BUFFER_SIZE)) != -1 ) {
						bufferedOutputStream.write(buffer, 0, count);
					}
					bufferedOutputStream.flush();
					bufferedOutputStream.close();
				}
			}

			if(fUnzip.getName().endsWith("python")
					|| fUnzip.getName().endsWith(".so")
					|| fUnzip.getName().endsWith(".xml")
					|| fUnzip.getName().endsWith(".py")
					|| fUnzip.getName().endsWith(".zip")) {
				MiscUtils.chmod(fUnzip, 0755);
			}
			Log.d(TAG,"Unpacked " + zipEntryName);
		}
		zipInputStream.close();

		if(deleteInput) {
			boolean success = inputFile.delete();
			Log.d(TAG, "delete input: success=" + success + " path=" + inputFile.getAbsolutePath());
		}
	}
}
