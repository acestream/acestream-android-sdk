package org.acestream.sdk.utils;

import android.content.Intent;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("WeakerAccess")
public class VlcBridge {

    // Target actions
    public static final String ACTION_START_PLAYBACK_SERVICE = "org.acestream.vlc.bridge.start_playback_service";
    public static final String ACTION_START_MAIN_ACTVITY = "org.acestream.vlc.bridge.start_main_activity";
    public static final String ACTION_CLOSE_PLAYER = "org.acestream.vlc.bridge.close_player";

    // Subactions
    public static final String ACTION_LOAD_P2P_PLAYLIST = "LOAD_P2P_PLAYLIST";
    public static final String ACTION_SAVE_P2P_PLAYLIST = "SAVE_P2P_PLAYLIST";
    public static final String ACTION_SAVE_METADATA = "SAVE_METADATA";
    public static final String ACTION_STOP_PLAYBACK = "STOP_PLAYBACK";

    // Params
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_PLAYER = "player";
    public static final String EXTRA_DESCRIPTOR = "descriptor";
    public static final String EXTRA_PLAYLIST_POSITION = "playlistPosition";
    public static final String EXTRA_ASK_RESUME = "askResume";
    public static final String EXTRA_METADATA = "metadata";
    public static final String EXTRA_MEDIA_FILES = "mediaFiles";
    public static final String EXTRA_REMOTE_CLIENT_ID = "remoteClientId";
    public static final String EXTRA_SEEK_ON_START = "seekOnStart";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_SKIP_PLAYER = "skipPlayer";
    public static final String EXTRA_SKIP_RESETTINGS_DEVICES = "skipResettingDevices";
    public static final String EXTRA_FRAGMENT_ID = "fragmentId";

    // Predefined values
    public static final String FRAGMENT_VIDEO_LOCAL = "VIDEO_LOCAL";
    public static final String FRAGMENT_VIDEO_TORRENTS = "VIDEO_TORRENTS";
    public static final String FRAGMENT_VIDEO_LIVE_STREAMS = "VIDEO_LIVE_STREAMS";
    public static final String FRAGMENT_AUDIO_LOCAL = "AUDIO_LOCAL";
    public static final String FRAGMENT_AUDIO_TORRENTS = "AUDIO_TORRENTS";
    public static final String FRAGMENT_BROWSING_DIRECTORIES = "BROWSING_DIRECTORIES";
    public static final String FRAGMENT_BROWSING_LOCAL_NETWORKS = "BROWSING_LOCAL_NETWORKS";
    public static final String FRAGMENT_BROWSING_STREAM = "BROWSING_STREAM";
    public static final String FRAGMENT_HISTORY = "HISTORY";
    public static final String FRAGMENT_ABOUT = "ABOUT";
    public static final String FRAGMENT_SETTINGS_ADS = "SETTINGS_ADS";
    public static final String FRAGMENT_SETTINGS_ENGINE = "SETTINGS_ENGINE";
    public static final String FRAGMENT_SETTINGS_PLAYER = "SETTINGS_PLAYER";

    private static String sTargetPackage = null;

    // Client must set target package to use this class
    public static void setTargetPackage(@Nullable String targetPackage) {
        sTargetPackage = targetPackage;
    }

    private static Intent getTargetIntent(String targetAction) {
        return getTargetIntent(targetAction, null);
    }

    private static Intent getTargetIntent(String targetAction, String subAction) {
        if(sTargetPackage == null) {
            throw new IllegalStateException("Target package is not set");
        }

        // Use android action to start service and pass real action as extra.
        Intent intent = new Intent(targetAction);
        intent.setPackage(sTargetPackage);
        if(subAction != null) {
            intent.putExtra(EXTRA_ACTION, subAction);
        }
        return intent;
    }

    public static String getAction(Intent intent) {
        return intent.getStringExtra(EXTRA_ACTION);
    }

    public static class LoadP2PPlaylistIntentBuilder {
        private final Intent mIntent;

        public LoadP2PPlaylistIntentBuilder(@NonNull TransportFileDescriptor descriptor) {
            mIntent = getTargetIntent(ACTION_START_PLAYBACK_SERVICE, ACTION_LOAD_P2P_PLAYLIST);
            mIntent.putExtra(EXTRA_DESCRIPTOR, descriptor.toJson());
        }

        public LoadP2PPlaylistIntentBuilder setPlayer(@NonNull SelectedPlayer player) {
            mIntent.putExtra(EXTRA_PLAYER, player.toJson());
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setPlaylistPosition(int position) {
            mIntent.putExtra(EXTRA_PLAYLIST_POSITION, position);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setAskResume(boolean value) {
            mIntent.putExtra(EXTRA_ASK_RESUME, value);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setStart(boolean value) {
            mIntent.putExtra(EXTRA_START, value);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setSkipPlayer(boolean value) {
            mIntent.putExtra(EXTRA_SKIP_PLAYER, value);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setSkipResettingDevices(boolean value) {
            mIntent.putExtra(EXTRA_SKIP_RESETTINGS_DEVICES, value);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setMetadata(@Nullable MediaFilesResponse metadata) {
            if(metadata != null) {
                mIntent.putExtra(EXTRA_METADATA, metadata.toJson());
            }
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setMediaFile(@NonNull MediaFilesResponse.MediaFile mediaFile) {
            String[] mediaFilesArray = { mediaFile.toJson() };
            mIntent.putExtra(EXTRA_MEDIA_FILES, mediaFilesArray);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setMediaFiles(@NonNull MediaFilesResponse.MediaFile[] mediaFiles) {
            String[] mediaFilesArray = new String[mediaFiles.length];
            for (int i = 0; i < mediaFiles.length; i++) {
                mediaFilesArray[i] = mediaFiles[i].toJson();
            }
            mIntent.putExtra(EXTRA_MEDIA_FILES, mediaFilesArray);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setRemoteClientId(String remoteClientId) {
            mIntent.putExtra(EXTRA_REMOTE_CLIENT_ID, remoteClientId);
            return this;
        }

        public LoadP2PPlaylistIntentBuilder setSeekOnStart(long seekOnStart) {
            mIntent.putExtra(EXTRA_SEEK_ON_START, seekOnStart);
            return this;
        }

        public void send() {
            AceStream.context().sendBroadcast(mIntent);
        }
    }

    public static void saveP2PPlaylist(
            @NonNull TransportFileDescriptor descriptor,
            @Nullable MediaFilesResponse metadata,
            MediaFilesResponse.MediaFile mediaFile) {
        Intent intent = getTargetIntent(ACTION_START_PLAYBACK_SERVICE, ACTION_SAVE_P2P_PLAYLIST);
        intent.putExtra(EXTRA_DESCRIPTOR, descriptor.toJson());
        String[] mediaFilesArray = {mediaFile.toJson()};
        intent.putExtra(EXTRA_MEDIA_FILES, mediaFilesArray);
        if(metadata != null) {
            intent.putExtra(EXTRA_METADATA, metadata.toJson());
        }
        AceStream.context().sendBroadcast(intent);
    }

    public static void stopPlayback(boolean systemExit, boolean clearPlaylist, boolean saveMetadata) {
        Intent intent = getTargetIntent(ACTION_START_PLAYBACK_SERVICE, ACTION_STOP_PLAYBACK);
        intent.putExtra("systemExit", systemExit);
        intent.putExtra("clearPlaylist", clearPlaylist);
        intent.putExtra("saveMetadata", saveMetadata);
        AceStream.context().sendBroadcast(intent);
    }

    public static void saveMetadata() {
        Intent intent = getTargetIntent(ACTION_START_PLAYBACK_SERVICE, ACTION_SAVE_METADATA);
        AceStream.context().sendBroadcast(intent);
    }

    public static Intent getMainActivityIntent() {
        return getTargetIntent(ACTION_START_MAIN_ACTVITY);
    }

    public static void openMainActivity() {
        AceStream.context().sendBroadcast(getTargetIntent(ACTION_START_MAIN_ACTVITY));
    }

    public static void openVideoLocal() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_VIDEO_LOCAL);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openVideoTorrents() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_VIDEO_TORRENTS);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openVideoLiveStreams() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_VIDEO_LIVE_STREAMS);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openAudioLocal() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_AUDIO_LOCAL);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openAudioTorrents() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_AUDIO_TORRENTS);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openBrowsingDirectories() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_BROWSING_DIRECTORIES);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openBrowsingLocalNetworks() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_BROWSING_LOCAL_NETWORKS);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openBrowsingStream() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_BROWSING_STREAM);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openHistory() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_HISTORY);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openAbout() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_ABOUT);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openSettingsAds() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_SETTINGS_ADS);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openSettingsEngine() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_SETTINGS_ENGINE);
        AceStream.context().sendBroadcast(intent);
    }

    public static void openSettingsPlayer() {
        Intent intent = getTargetIntent(ACTION_START_MAIN_ACTVITY);
        intent.putExtra(EXTRA_FRAGMENT_ID, FRAGMENT_SETTINGS_PLAYER);
        AceStream.context().sendBroadcast(intent);
    }
    public static void closeVlcPlayer() {
        Intent intent = new Intent(ACTION_CLOSE_PLAYER);
        AceStream.context().sendBroadcast(intent);
    }
}
