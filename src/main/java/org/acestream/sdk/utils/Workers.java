package org.acestream.sdk.utils;

import android.os.Handler;
import android.os.Looper;

public class Workers {
    private static final Handler sMainThreadHandler = new Handler(Looper.getMainLooper());

    public static boolean isOnMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static void runOnMainThread(Runnable runnable) {
        if (isOnMainThread()) {
            runnable.run();
        }
        else {
            sMainThreadHandler.post(runnable);
        }
    }
}
