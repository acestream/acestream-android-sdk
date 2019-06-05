package org.acestream.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.widget.Toast;

import org.acestream.engine.ServiceClient;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.interfaces.IBaseApplicationFactory;
import org.acestream.sdk.utils.DeviceInfo;
import org.acestream.sdk.utils.EnvironmentUtils;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.PermissionUtils;
import org.acestream.sdk.utils.Workers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import static org.acestream.sdk.Constants.EXTRA_SELECTED_PLAYER;
import static org.acestream.sdk.Constants.EXTRA_TRANSPORT_DESCRIPTOR;

@SuppressWarnings({"WeakerAccess", "unused"})
public class AceStream {
    private final static String TAG = "AS/SDK";

    private final static String PACKAGE_PREFIX = "org.acestream.";

    // Incoming intent actions
    public final static String ACTION_START_PLAYER = PACKAGE_PREFIX + "action.start_player";
    public final static String ACTION_START_CONTENT = PACKAGE_PREFIX + "action.start_content";
    public final static String ACTION_OPEN_RESOLVER = PACKAGE_PREFIX + "action.open_resolver";
    public final static String ACTION_OPEN_PROFILE_ACTIVITY = PACKAGE_PREFIX + "action.open_profile_activity";
    public final static String ACTION_OPEN_TOPUP_ACTIVITY = PACKAGE_PREFIX + "action.open_topup_activity";
    public final static String ACTION_OPEN_UPGRADE_ACTIVITY = PACKAGE_PREFIX + "action.open_upgrade_activity";
    public final static String ACTION_OPEN_REPORT_PROBLEM_ACTIVITY = PACKAGE_PREFIX + "action.open_report_problem_activity";
    public final static String ACTION_OPEN_REMOTE_CONTROL_ACTIVITY = PACKAGE_PREFIX + "action.open_remote_control_activity";
    public final static String ACTION_OPEN_BONUS_ADS_ACTIVITY = PACKAGE_PREFIX + "action.open_bonus_ads_activity";
    public final static String ACTION_OPEN_LOGIN_ACTIVITY = PACKAGE_PREFIX + "action.open_login_activity";

    public final static String ACTION_RESTART_APP = PACKAGE_PREFIX + "action.restart_app";
    public final static String ACTION_STOP_APP = PACKAGE_PREFIX + "action.stop_app";
    public final static String BROADCAST_APP_IN_BACKGROUND = PACKAGE_PREFIX + "broadcast.app_in_background";

    public final static String EXTRA_CURRENT_MEDIA_URI = "current_media_uri";
    public final static String EXTRA_LOGIN_TARGET = "login_target";

    public final static String LOGIN_TARGET_BONUS_ADS = "bonus_ads";

    @SuppressLint("StaticFieldLeak")
    private static Context sContext;
    private static LruCache<String,String> sTransportFileCache = null;
    private static String sDeviceUuid;
    private static String sHttpApiProductKey;
    private static SelectedPlayer mLastSelectedPlayer = null;
    private static boolean sTestMode = false;
    private static File sDefaultCacheDir = null;
    private static File sAppExternalFilesDir = null;
    private static String sAppFilesDir;
    private static String sApplicationId;
    private static String sBackendDomain = "https://m.acestream.net";
    private static boolean sStorageAccessGranted = false;
    private static int sApplicationVersionCode = -1;
    private static String sApplicationVersionName = null;
    private static IBaseApplicationFactory sBaseApplicationFactory = null;

    public static void init(Context context, String appFilesDir, String deviceName, String deviceUuid) {
        sContext = context.getApplicationContext();
        sApplicationId = sContext.getPackageName();
        sApplicationVersionCode = MiscUtils.getAppVersionCode(sContext, sApplicationId);
        sApplicationVersionName = MiscUtils.getAppVersionName(sContext, sApplicationId);
        sDeviceUuid = deviceUuid;
        sAppFilesDir = appFilesDir;
        sTransportFileCache = new LruCache<>(4 * 1024 * 1024);
    }

    public static void setHttpApiProductKey(String key) {
        sHttpApiProductKey = key;
    }

    public static void setBackendDomain(String domain) {
        sBackendDomain = domain;
    }

    public static String getHttpApiProductKey() {
        return sHttpApiProductKey;
    }

    public static String getDeviceUuidString() {
        return sDeviceUuid;
    }

    public static Context context() {
        if(sContext == null) {
            throw new IllegalStateException("AceStream.init() was not called");
        }
        return sContext;
    }

    public static String getTransportFileFromCache(String key) {
        if(key == null) {
            return null;
        }

        try {
            return sTransportFileCache.get(key);
        }
        catch(Throwable e) {
            Log.e(TAG, "getTransportFileFromCache: error", e);
            return null;
        }
    }

    public static void putTransportFileToCache(String key, String data) {
        if(key == null || data == null) {
            return;
        }

        try {
            sTransportFileCache.put(key, data);
        }
        catch(Throwable e) {
            Log.e(TAG, "putTransportFileToCache: error", e);
        }
    }

    public static File getTransportFilesDir() {
        return getTransportFilesDir(false);
    }

    public static File getTransportFilesDir(boolean ensureExists) {
        return getAppFilesDir("transport_files", ensureExists);
    }

    public static File getAppFilesDir(String dirname, boolean ensureExists) {
        File dir = new File(context().getFilesDir(), dirname);
        if(ensureExists && !dir.exists()) {
            if(!dir.mkdir()) {
                throw new IllegalStateException("Failed to create app files dir");
            }
        }
        return dir;
    }

    public static void setLastSelectedPlayer(@NonNull SelectedPlayer player) {
        Logger.v(TAG, "setLastSelectedPlayer: " + player.toString());
        mLastSelectedPlayer = player;
    }

    public static SelectedPlayer getLastSelectedPlayer() {
        return mLastSelectedPlayer;
    }

    @Nullable
    public static String getTargetApp() {
        try {
            String mainApp = ServiceClient.getServicePackage(context());
            if (TextUtils.equals(getApplicationId(), mainApp)) {
                Log.d(TAG, "i am target app: id=" + mainApp + " this=" + getApplicationId());
                return null;
            } else {
                Log.d(TAG, "get target app: id=" + mainApp + " this=" + getApplicationId());
                return mainApp;
            }
        }
        catch(ServiceClient.ServiceMissingException e) {
            return null;
        }
    }

    public static boolean isAndroidTv() {
        return DeviceInfo.isAndroidTv || !DeviceInfo.hasTsp;
    }

    public static List<SelectedPlayer> getAvailablePlayers() {
        return getAvailablePlayers(true);
    }

    public static List<SelectedPlayer> getAvailablePlayers(boolean excludeOurPlayer) {
        List<SelectedPlayer> availablePlayers = new ArrayList<>();
        Set<String> foundPlayers = new HashSet<>();

        availablePlayers.add(SelectedPlayer.getOurPlayer());

        List<ResolveInfo> knownPlayers = getKnownPlayers();
        for(ResolveInfo ri: knownPlayers) {
            foundPlayers.add(ri.activityInfo.packageName);
            availablePlayers.add(SelectedPlayer.fromResolveInfo(context(), ri));
        }

        List<ResolveInfo> installedPlayers = getInstalledPlayers();
        for (ResolveInfo ri : installedPlayers) {
            if(excludeOurPlayer && ri.activityInfo.packageName.startsWith("org.acestream.")) {
                continue;
            }

            if (!foundPlayers.contains(ri.activityInfo.packageName)) {
                availablePlayers.add(SelectedPlayer.fromResolveInfo(context(), ri));
            }
        }

        return availablePlayers;
    }

    public static List<ResolveInfo> getKnownPlayers() {
        Intent intent;
        ResolveInfo ri;
        List<ResolveInfo> players = new ArrayList<>();
        PackageManager pm = context().getPackageManager();

        String[] knownPlayers = {
                Constants.VLC_PACKAGE_NAME,
                Constants.VLC_BETA_PACKAGE_NAME,
                Constants.VLC_DEBUG_PACKAGE_NAME,
                Constants.MX_FREE_PACKAGE_NAME,
                Constants.MX_PRO_PACKAGE_NAME
        };

        for(String packageName: knownPlayers) {
            intent = new Intent();
            intent.setPackage(packageName);

            // use test intent
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("http://example.com/test.mp4"), "video/mp4");
            intent.addCategory(Intent.CATEGORY_BROWSABLE);

            ri = pm.resolveActivity(intent, 0);
            if(ri != null) {
                players.add(ri);
            }
        }

        return players;
    }

    @NonNull
    public static List<ResolveInfo> getInstalledPlayers() {
        Intent intent;
        List<Intent> intents = new ArrayList<>();

        intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.withAppendedPath(MediaStore.Video.Media.INTERNAL_CONTENT_URI, "1"), "video/*");
        intents.add(intent);

        intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("http://example.com/test.mp4"), "video/mp4");
        intents.add(intent);

        return MiscUtils.resolveActivityIntents(context(), intents);
    }

    public static boolean isAppInstalled(String packageName) {
        boolean installed = false;
        try {
            context().getPackageManager().getApplicationInfo(packageName, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            // ignore
        }
        return installed;
    }

    public static boolean shouldDiscoverDevices() {
        return !isAndroidTv();
    }

    public static boolean isAceStreamUrl(@Nullable Uri uri) {
        return parseAceStreamHttpApiUrl(uri) != null || parseAceStreamPlaybackUrl(uri) != null;
    }

    @Nullable
    public static String parseAceStreamHttpApiUrl(@Nullable Uri uri) {
        if(uri == null) {
            return null;
        }
        else {
            return parseAceStreamHttpApiUrl(uri.toString());
        }
    }

    /**
     * Convert HTTP API URL to acestream:
     * @param url
     * @return String
     */
    @Nullable
    public static String parseAceStreamHttpApiUrl(@Nullable String url) {
        if(url == null) {
            Log.v(TAG, "parseAceStreamHttpApiUrl: null url");
            return null;
        }

        Pattern p = Pattern.compile("/(?:ace|hls)/(?:getstream|manifest\\.m3u8)");
        Matcher m = p.matcher(url);
        if(!m.find()) {
            Logger.vv(TAG, "parseAceStreamHttpApiUrl: no match: url=" + url);
            return null;
        }

        Logger.vv(TAG, "parseAceStreamHttpApiUrl: got match: url=" + url);

        Map<String, String> params;
        try {
            params = MiscUtils.getQueryParameters(url);
        }
        catch(Exception e) {
            Logger.e(TAG, "failed to parse url: " + url, e);
            return null;
        }

        //TODO: add support of 'manifest_url' param
        String acestreamLink = null;
        for(Map.Entry<String, String> item: params.entrySet()) {
            switch (item.getKey()) {
                case "id":
                case "content_id":
                    acestreamLink = "acestream:?content_id=" + Uri.encode(item.getValue());
                    break;
                case "url":
                    acestreamLink = "acestream:?url=" + Uri.encode(item.getValue());
                    break;
                case "magnet":
                    acestreamLink = "acestream:?magnet=" + Uri.encode(item.getValue());
                    break;
                case "infohash":
                    acestreamLink = "acestream:?infohash=" + Uri.encode(item.getValue());
                    break;
            }
        }

        if(acestreamLink == null) {
            Logger.e(TAG, "Failed to detect url: " + url);
        }

        return acestreamLink;
    }

    public static String parseAceStreamPlaybackUrl(@Nullable Uri uri) {
        if(uri == null) {
            return null;
        }
        else {
            return parseAceStreamPlaybackUrl(uri.toString());
        }
    }

    @Nullable
    public static String parseAceStreamPlaybackUrl(@Nullable String url) {
        if(url == null) {
            Log.v(TAG, "parseAceStreamPlaybackUrl: null url");
            return null;
        }

        final Pattern[] patterns = new Pattern[2];
        patterns[0] = Pattern.compile("/(?:ace|hls)/[rm]/([0-9a-f]+)/[0-9a-f]+");
        patterns[1] = Pattern.compile("/content/([0-9a-f]+)/\\d+\\.\\d+");

        for(Pattern p: patterns) {
            Matcher m = p.matcher(url);
            if(m.find()) {
                String hash = m.group(1);
                Logger.vv(TAG, "parseAceStreamPlaybackUrl: got match: url=" + url + " hash=" + hash);
                return hash;
            }
        }

        Logger.vv(TAG, "parseAceStreamPlaybackUrl: no match: url=" + url);

        return null;
    }

    public static void assertTestMode() {
        if(!sTestMode) {
            throw new IllegalStateException("test mode expected");
        }
    }

    public static String getEnginePlayerId() {
        return "engineProxy-" + (new Random().nextInt());
    }

    public static boolean isTestMode() {
        return sTestMode;
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    public static void setTestMode(boolean value) {
        sTestMode = value;
    }

    public static class Resolver {
        public final static int RESULT_CLOSE_CALLER = Activity.RESULT_FIRST_USER + 1;

        public static class IntentBuilder {
            private Intent mIntent;

            public IntentBuilder(@NonNull Context context,
                                 @NonNull String infohash,
                                 @NonNull String contentType,
                                 @NonNull String mime) {
                int isLive = (TextUtils.equals(contentType, Constants.CONTENT_TYPE_LIVE)) ? 1 : 0;
                mIntent = new Intent(ACTION_OPEN_RESOLVER);
                mIntent.putExtra(Constants.EXTRA_INFOHASH, infohash);
                mIntent.putExtra(Constants.EXTRA_MIME, mime);
                mIntent.putExtra(Constants.EXTRA_IS_LIVE, isLive);
            }

            public IntentBuilder showAceStreamPlayer(boolean value) {
                mIntent.putExtra(Constants.EXTRA_SHOW_ACESTREAM_PLAYER, value);
                return this;
            }

            public IntentBuilder showOnlyKnownPlayers(boolean value) {
                mIntent.putExtra(Constants.EXTRA_SHOW_ONLY_KNOWN_PLAYERS, value);
                return this;
            }

            public IntentBuilder allowRememberPlayer(boolean value) {
                mIntent.putExtra(Constants.EXTRA_ALLOW_REMEMBER_PLAYER, value);
                return this;
            }

            public Intent build() {
                return mIntent;
            }
        }
    }

    public static Intent makeIntentFromDescriptor(
            @NonNull TransportFileDescriptor descriptor,
            @Nullable SelectedPlayer player) {
        Intent intent = new Intent(ACTION_START_CONTENT);
        intent.putExtra(EXTRA_TRANSPORT_DESCRIPTOR, descriptor.toJson());
        if(player != null) {
            intent.putExtra(EXTRA_SELECTED_PLAYER, player.toJson());
        }

        return intent;
    }

    public static Intent makeIntentFromUri(
            @NonNull Context context,
            @NonNull Uri uri,
            @Nullable SelectedPlayer player,
            boolean startedFromExternalRequest,
            boolean skipRememberedPlayer) {

        if(!TextUtils.equals(uri.getScheme(), "acestream")) {
            throw new IllegalStateException("acestream: scheme expected");
        }

        Intent intent = new Intent(ACTION_START_CONTENT);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.putExtra(Constants.EXTRA_STARTED_FROM_EXTERNAL_REQUEST, startedFromExternalRequest);
        intent.putExtra(Constants.EXTRA_SKIP_REMEMBERED_PLAYER, skipRememberedPlayer);
        if(player != null) {
            intent.putExtra(EXTRA_SELECTED_PLAYER, player.toJson());
        }
        return intent;
    }

    public static void toast(int resId) {
        toast(context().getString(resId));
    }

    public static void toast(final String message) {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static List<CacheDirLocation> getCacheDirLocations() {
        List<CacheDirLocation> locations = new ArrayList<>();
        Resources res = context().getResources();

        if(sDefaultCacheDir != null && sDefaultCacheDir.canWrite()) {
            locations.add(new CacheDirLocation(res.getString(R.string.device_storage), sDefaultCacheDir.getAbsolutePath()));
        }
        else if(canWriteToExternalFilesDir()) {
            locations.add(new CacheDirLocation(res.getString(R.string.device_storage), externalFilesDir()));
        }

        List<File> files = EnvironmentUtils.getExternalSdCards(context());
        int i = 0;
        String sdCardName;
        final String sdCardBaseName = res.getString(R.string.sd_card);
        for(File file: files) {
            ++i;
            if(files.size() > 1) {
                sdCardName = sdCardBaseName + " " + i;
            }
            else {
                sdCardName = sdCardBaseName;
            }
            locations.add(new CacheDirLocation(sdCardName, file.getAbsolutePath()));
        }

        return locations;
    }

    public static boolean canWriteToExternalFilesDir() {
        if(sAppExternalFilesDir == null) {
            return false;
        }
        else {
            return sAppExternalFilesDir.canWrite();
        }
    }

    public static void onStorageAccessGranted() {
        sStorageAccessGranted = true;
        initWorkingDirs();
    }

    public static void initWorkingDirs() {
        sAppExternalFilesDir = new File(Environment.getExternalStorageDirectory(), "org.acestream.engine");
        if (!sAppExternalFilesDir.exists()) {
            if(!sAppExternalFilesDir.mkdirs()) {
                Log.w(TAG, "failed to create missing dir: " + sAppExternalFilesDir.getAbsolutePath());
                sAppExternalFilesDir = null;
            }
        }

        if(sAppExternalFilesDir != null && !sAppExternalFilesDir.canWrite()) {
            Log.w(TAG, "dir is not writable: " + sAppExternalFilesDir.getAbsolutePath());
            sAppExternalFilesDir = null;
        }

        if(sAppExternalFilesDir == null) {
            sAppExternalFilesDir = context().getExternalFilesDir(null);
            sDefaultCacheDir = sAppExternalFilesDir;
        }
        else {
            // use old style cache dir /sdcard/org.acestream.engine/.ACEStream
            sDefaultCacheDir = new File(sAppExternalFilesDir, ".ACEStream");
            if (!sDefaultCacheDir.exists()) {
                if(!sDefaultCacheDir.mkdirs()) {
                    Log.w(TAG, "failed to created missing dir: " + sDefaultCacheDir.getAbsolutePath());
                    sDefaultCacheDir = null;
                }
            }

            if(sDefaultCacheDir != null && !sDefaultCacheDir.canWrite()) {
                Log.w(TAG, "dir is not writable: " + sDefaultCacheDir.getAbsolutePath());
                sDefaultCacheDir = null;
            }

            if(sDefaultCacheDir == null) {
                sDefaultCacheDir = sAppExternalFilesDir;
            }
        }

        if(sAppExternalFilesDir == null) {
            Log.i(TAG, "no working dir");
        }
        else {
            Log.i(TAG, "working dir: " + sAppExternalFilesDir.getAbsolutePath());
        }
    }

    public static String filesDir() {
        return sAppFilesDir;
    }

    public static String externalFilesDir() {
        if(sAppExternalFilesDir == null) {
            return null;
        }
        else {
            return sAppExternalFilesDir.getAbsolutePath();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean checkStorageAccess() {
        boolean has = PermissionUtils.hasStorageAccess();
        if(has && !sStorageAccessGranted) {
            // This situation is possible when user has granted permissions from system settings
            onStorageAccessGranted();
        }
        return has;
    }

    public static void openReportProblemActivity(Context context) {
        startActivity(context, ACTION_OPEN_REPORT_PROBLEM_ACTIVITY);
    }

    public static void openRemoteControlActivity(Context context, Uri currentMediaUri) {
        Intent intent = new Intent(ACTION_OPEN_REMOTE_CONTROL_ACTIVITY);
        intent.putExtra(EXTRA_CURRENT_MEDIA_URI, currentMediaUri);
        startActivity(context, intent);
    }

    public static void openBonusAdsActivity(Context context) {
        startActivity(context, ACTION_OPEN_BONUS_ADS_ACTIVITY);
    }

    public static void openProfileActivity(Context context) {
        startActivity(context, ACTION_OPEN_PROFILE_ACTIVITY);
    }

    public static void openLoginActivity(Context context, String target) {
        Intent intent = new Intent(ACTION_OPEN_LOGIN_ACTIVITY);
        intent.putExtra(EXTRA_LOGIN_TARGET, target);
        startActivity(context, intent);
    }

    public static void openTopupActivity(Context context) {
        sendBroadcast(context, ACTION_OPEN_TOPUP_ACTIVITY);
    }

    public static void openUpgradeActivity(Context context) {
        sendBroadcast(context, ACTION_OPEN_UPGRADE_ACTIVITY);
    }

    private static void startActivity(Context context, String action) {
        startActivity(context, new Intent(action));
    }

    private static void startActivity(Context context, Intent intent) {
        try {
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        }
        catch(ActivityNotFoundException e) {
            AceStream.toast("Activity not found: " + intent.getAction());
        }
    }

    private static void sendBroadcast(Context context, String action) {
        try {
            Intent intent = new Intent(action);
            intent.setPackage(ServiceClient.getServicePackage(context));
            context.sendBroadcast(intent);
        }
        catch (ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "sendBroadcast: AceStream is not installed");
        }
    }

    public static String getApplicationId() {
        return sApplicationId;
    }

    public static int getApplicationVersionCode() {
        return sApplicationVersionCode;
    }

    public static String getApplicationVersionName() {
        return sApplicationVersionName;
    }

    public static int getSdkVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    public static String getErrorMessage(String errorId) {
        int stringId = 0;
        switch(errorId) {
            case "auth_internal_error":
                stringId = R.string.auth_internal_error;
                break;
            case "auth_network_error":
                stringId = R.string.auth_network_error;
                break;
            case "auth_failed_error":
                stringId = R.string.auth_failed_error;
                break;
            case "auth_error_user_not_found":
                stringId = R.string.auth_error_user_not_found;
                break;
            case "auth_error_user_disabled":
                stringId = R.string.auth_error_user_disabled;
                break;
            case "auth_error_bad_password":
                stringId = R.string.auth_error_bad_password;
                break;
        }

        if(stringId == 0) {
            return errorId;
        }
        else {
            return context().getString(stringId);
        }
    }

    public static String getBackendDomain() {
        return sBackendDomain;
    }

    public static boolean isInstalled() {
        List<ResolveInfo> ri = MiscUtils.resolveActivityIntent(context(), new Intent(ACTION_START_PLAYER));
        return ri.size() > 0;
    }

    public static void restartApp() {
        Intent intent = new Intent(ACTION_RESTART_APP);
        context().sendBroadcast(intent);
    }

    public static Intent getStopAppIntent() {
        return new Intent(ACTION_STOP_APP);
    }

    public static void stopApp() {
        Logger.d(TAG, "stopApp");
        context().sendBroadcast(getStopAppIntent());
    }

    public static void setBaseApplicationFactory(IBaseApplicationFactory factory) {
        sBaseApplicationFactory = factory;
    }

    public static IBaseApplicationFactory getBaseApplicationFactory() {
        return sBaseApplicationFactory;
    }
}
