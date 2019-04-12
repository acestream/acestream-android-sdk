package org.acestream.sdk.utils;

import android.util.Log;

import org.acestream.sdk.BuildConfig;

public class Logger {
    private static int sLogLevel = Log.INFO;

    public static void enableDebugLogging(boolean enable) {
        setLogLevel(enable ? Log.VERBOSE : Log.INFO);
    }

    private static void setLogLevel(int level) {
        sLogLevel = level;
    }

    public static boolean verbose() {
        return sLogLevel <= android.util.Log.VERBOSE;
    }

    public static void v(String tag, String message, Throwable e) {
        if(verbose()) {
            android.util.Log.v(tag, message, e);
        }
    }

    public static void v(String tag, String message) {
        v(tag, message, null);
    }

    public static void d(String tag, String message) {
        if(sLogLevel <= android.util.Log.DEBUG) {
            android.util.Log.d(tag, message);
        }
    }

    public static void vv(String tag, String message, Throwable e) {
        if(BuildConfig.DEBUG) {
            v(tag, message);
        }
    }

    public static void vv(String tag, String message) {
        vv(tag, message, null);
    }

    public static void debugAssert(boolean value, String tag, String message) {
        if(!value) {
            if(BuildConfig.DEBUG) {
                throw new IllegalStateException(message);
            }
            else {
                Log.e(tag, message);
            }
        }
    }
}
