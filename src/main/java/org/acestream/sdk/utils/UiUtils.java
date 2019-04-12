package org.acestream.sdk.utils;

import android.app.Activity;

import org.acestream.sdk.R;

public class UiUtils {
    public static void applyOverscanMargin(Activity activity) {
        final int hm = activity.getResources().getDimensionPixelSize(R.dimen.tv_overscan_horizontal);
        final int vm = activity.getResources().getDimensionPixelSize(R.dimen.tv_overscan_vertical);
        activity.findViewById(android.R.id.content).setPadding(hm, vm, hm, vm);
    }
}
