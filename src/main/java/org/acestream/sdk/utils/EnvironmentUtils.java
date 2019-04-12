package org.acestream.sdk.utils;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class EnvironmentUtils {
    public static List<File> getExternalSdCards(Context context) {
        ArrayList<File> result = new ArrayList<>();
        File[] files;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            files = context.getExternalFilesDirs(null);
        }
        else {
            List<File> fileList = new ArrayList<>();
            String[] dirs = getStorageDirectories(false);
            for(String dir: dirs) {
                File file = new File(dir);
                if(file.exists() && file.isDirectory()) {
                    fileList.add(file);
                }
            }
            files = fileList.toArray(new File[0]);
        }

        for(File f: files) {
            if(f != null) {
                boolean isEmulated = isExternalStorageEmulated(f);
                boolean isWritable = f.canWrite();

                if (!isEmulated && isWritable) {
                    result.add(f);
                }
            }
        }

        return result;
    }

    public static boolean isExternalStorageEmulated(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return android.os.Environment.isExternalStorageEmulated(file);
        }
        else {
            File extStorage = android.os.Environment.getExternalStorageDirectory();
            return file.getAbsolutePath().startsWith(extStorage.getAbsolutePath());
        }
    }

    /**
     * Returns all available SD-Cards in the system (include emulated)
     *
     * Warning: Hack! Based on Android source code of version 4.3 (API 18)
     * Because there is no standart way to get it.
     * TODO: Test on future Android versions 4.4+
     *
     * @return paths to all available SD-Cards in the system (include emulated)
     */
    public static String[] getStorageDirectories(boolean includePrimary)
    {
        final Pattern DIR_SEPARATOR = Pattern.compile("/");

        // Final set of paths
        final Set<String> rv = new HashSet<String>();
        // Primary physical SD-CARD (not emulated)
        final String rawExternalStorage = System.getenv("EXTERNAL_STORAGE");
        // All Secondary SD-CARDs (all exclude primary) separated by ":"
        final String rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE");
        // Primary emulated SD-CARD
        final String rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET");

        if(includePrimary) {
            if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
                // Device has physical external storage; use plain paths.
                if (TextUtils.isEmpty(rawExternalStorage)) {
                    // EXTERNAL_STORAGE undefined; falling back to default.
                    rv.add("/storage/sdcard0");
                } else {
                    rv.add(rawExternalStorage);
                }
            } else {
                // Device has emulated storage; external storage paths should have
                // userId burned into them.
                final String rawUserId;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    rawUserId = "";
                } else {
                    final String path = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
                    final String[] folders = DIR_SEPARATOR.split(path);
                    final String lastFolder = folders[folders.length - 1];
                    boolean isDigit = false;
                    try {
                        Integer.valueOf(lastFolder);
                        isDigit = true;
                    } catch (NumberFormatException ignored) {
                    }
                    rawUserId = isDigit ? lastFolder : "";
                }
                // /storage/emulated/0[1,2,...]
                if (TextUtils.isEmpty(rawUserId)) {
                    rv.add(rawEmulatedStorageTarget);
                } else {
                    rv.add(rawEmulatedStorageTarget + File.separator + rawUserId);
                }
            }
        }

        // Add all secondary storages
        if(!TextUtils.isEmpty(rawSecondaryStoragesStr))
        {
            // All Secondary SD-CARDs splited into array
            final String[] rawSecondaryStorages = rawSecondaryStoragesStr.split(File.pathSeparator);
            Collections.addAll(rv, rawSecondaryStorages);
        }

        return rv.toArray(new String[0]);
    }
}
