package org.acestream.sdk.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.acestream.sdk.AceStream;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class PermissionUtils {

    public static boolean canWriteStorage() {
        return canWriteStorage(AceStream.context());
    }

    private static boolean canWriteStorage(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasStorageAccess() {
        return canWriteStorage();
    }

    public static void grantStoragePermissions(@NonNull Activity activity, int requestCode) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                requestStoragePermissions(activity, requestCode);
            } else {
                final Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                i.addCategory(Intent.CATEGORY_DEFAULT);
                i.setData(Uri.parse("package:" + AceStream.context().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    activity.startActivity(i);
                }
                catch (Exception ignored) {
                }
            }
        }
    }

    public static void requestStoragePermissions(Activity activity, int requestCode) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
            activity.requestPermissions(permissions, requestCode);
        }
    }
}
