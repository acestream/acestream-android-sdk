package org.acestream.sdk.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import org.acestream.sdk.AceStream;

public class DeviceInfo {
    public final static boolean hasTsp;
    public final static boolean isAndroidTv;

    static {
        final Context ctx = AceStream.context();
        final PackageManager pm = ctx != null ? ctx.getPackageManager() : null;
        hasTsp = pm == null || pm.hasSystemFeature("android.hardware.touchscreen");
        isAndroidTv = pm != null && pm.hasSystemFeature("android.software.leanback");
    }
}