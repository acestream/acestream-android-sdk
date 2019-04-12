package org.acestream.sdk;

public class Constants {
    public static final String ACTION_CONNECTION_AVAILABILITY_CHANGED = "org.acestream.engine.CONNECTION_AVAILABILITY_CHANGED";

    public static final int REMOTE_DEVICE_START_TIMEOUT = 65;
    public static final int REMOTE_DEVICE_CONNECT_TIMEOUT = 8;

    public final static int USER_LEVEL_OPTION_PREMIUM = 2;
    public final static int USER_LEVEL_OPTION_NO_ADS = 2<<1;
    public final static int USER_LEVEL_OPTION_PREMIUM_PLUS = 2<<2;
    public final static int USER_LEVEL_PACKAGE_BASIC = 2<<3;
    public final static int USER_LEVEL_PACKAGE_STANDARD = 2<<4;
    public final static int USER_LEVEL_PACKAGE_PREMIUM = 2<<5;
    public final static int USER_LEVEL_OPTION_PROXY_SERVER = 2<<6;
    public final static int USER_LEVEL_PACKAGE_SMART = 2<<7;
    public final static int USER_LEVEL_PACKAGE_SMART_ANDROID = 2<<8;

    public static final String MIME_HLS = "application/vnd.apple.mpegurl";
    public static final String MIME_JSON = "application/json";

    public static final String OUTPUT_FORMAT_AUTO = "auto";
    public static final String OUTPUT_FORMAT_HTTP = "http";
    public static final String OUTPUT_FORMAT_HLS = "hls";

    public static final String CONTENT_TYPE_VOD = "vod";
    public static final String CONTENT_TYPE_LIVE = "live";

    public static final String OUR_PLAYER_ID = "_acestream_";
    public static final String OUR_PLAYER_NAME = "Ace Player";

    public static final String ACESTREAM_PLAYER_SID = "acestream-player";

    public final static String DEFAULT_DEINTERLACE_MODE = "_disable_";
    public final static String DEINTERLACE_MODE_DISABLED = "_disable_";

    public static final String VLC_PACKAGE_NAME = "org.videolan.vlc";
    public static final String VLC_BETA_PACKAGE_NAME = "org.videolan.vlc.betav7neon";
    public static final String VLC_DEBUG_PACKAGE_NAME = "org.videolan.vlc.debug";
    public static final String MX_FREE_PACKAGE_NAME = "com.mxtech.videoplayer.ad";
    public static final String MX_PRO_PACKAGE_NAME = "com.mxtech.videoplayer.pro";

    public static final String PREFS_DEFAULT_OUTPUT_FORMAT_LIVE = OUTPUT_FORMAT_AUTO;
    public static final String PREFS_DEFAULT_OUTPUT_FORMAT_VOD = OUTPUT_FORMAT_AUTO;
    public static final boolean PREFS_DEFAULT_ALLOW_INTRANET_ACCESS = true;
    public static final boolean PREFS_DEFAULT_TRANSCODE_AUDIO = false;
    public static final boolean PREFS_DEFAULT_TRANSCODE_AC3 = false;
    public static final boolean PREFS_DEFAULT_ALLOW_REMOTE_ACCESS = false;
    public final static boolean PREF_DEFAULT_PAUSE_ON_AUDIOFOCUS_LOSS = false;

    public final static String PREF_KEY_GDPR_CONSENT = "gdpr_consent";
    public final static String PREF_KEY_SELECTED_PLAYER = "selected_player";
    public final static String PREF_KEY_LAST_SELECTED_PLAYER = "last_selected_player";
    public final static String PREF_KEY_DEINTERLACE_MODE = "deinterlace";
    public final static String PREF_KEY_SHOW_DEBUG_INFO = "show_debug_info";
    public final static String PREF_KEY_PAUSE_ON_AUDIOFOCUS_LOSS = "pause_on_audiofocus_loss";
    public final static String PREF_KEY_DEVICE_NAME = "device_name";

    public static final String EXTRA_INFOHASH = "org.acestream.EXTRA_INFOHASH";
    public static final String EXTRA_FILE_INDEX = "org.acestream.EXTRA_FILE_INDEX";
    public static final String EXTRA_SEEK_ON_START = "org.acestream.EXTRA_SEEK_ON_START";
    public static final String EXTRA_SELECTED_PLAYER = "org.acestream.EXTRA_SELECTED_PLAYER";
    public static final String EXTRA_MIME = "org.acestream.EXTRA_MIME";
    public static final String EXTRA_SHOW_ACESTREAM_PLAYER = "org.acestream.EXTRA_SHOW_ACESTREAM_PLAYER";
    public static final String EXTRA_SHOW_ONLY_KNOWN_PLAYERS = "org.acestream.EXTRA_SHOW_ONLY_KNOWN_PLAYERS";
    public static final String EXTRA_IS_LIVE = "org.acestream.EXTRA_IS_LIVE";
    public static final String EXTRA_TRANSPORT_DESCRIPTOR = "org.acestream.EXTRA_TRANSPORT_DESCRIPTOR";
    public static final String EXTRA_ERROR_MESSAGE = "org.acestream.EXTRA_ERROR_MESSAGE";
    public static final String EXTRA_ALLOW_REMEMBER_PLAYER = "org.acestream.EXTRA_ALLOW_REMEMBER_PLAYER";
    public static final String EXTRA_STARTED_FROM_EXTERNAL_REQUEST = "org.acestream.EXTRA_STARTED_FROM_EXTERNAL_REQUEST";
    public static final String EXTRA_SKIP_REMEMBERED_PLAYER = "org.acestream.EXTRA_SKIP_REMEMBERED_PLAYER";

    public final static String PREF_KEY_SHOW_REWARDED_ADS= "show_rewarded_ads";
    public final static boolean PREF_DEFAULT_SHOW_REWARDED_ADS = false;
    public final static String PREF_KEY_SHOW_ADS_ON_MAIN_SCREEN = "show_ads_on_main_screen";
    public final static boolean PREF_DEFAULT_SHOW_ADS_ON_MAIN_SCREEN = false;
    public final static String PREF_KEY_SHOW_ADS_ON_PREROLL = "show_ads_on_preroll";
    public final static boolean PREF_DEFAULT_SHOW_ADS_ON_PREROLL = false;
    public final static String PREF_KEY_SHOW_ADS_ON_PAUSE = "show_ads_on_pause";
    public final static boolean PREF_DEFAULT_SHOW_ADS_ON_PAUSE = false;
    public final static String PREF_KEY_SHOW_ADS_ON_CLOSE = "show_ads_on_close";
    public final static boolean PREF_DEFAULT_SHOW_ADS_ON_CLOSE = false;
}
