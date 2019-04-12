package org.acestream.sdk.interfaces;

import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineSessionStartListener;
import org.acestream.sdk.OutputFormat;
import org.acestream.sdk.PlaybackData;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.controller.api.response.AuthData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IAceStreamManager {
    interface PlaybackStateCallback {
        void onPlaylistUpdated();
        void onStart(@Nullable EngineSession session);
        void onPrebuffering(@Nullable EngineSession session, int progress);
        void onPlay(@Nullable EngineSession session);
        void onStop();
    }

    interface EngineStateCallback {
        void onEngineConnected(@NonNull IAceStreamManager playbackManager, @NonNull EngineApi engineApi);
    }

    interface AuthCallback {
        void onAuthUpdated(AuthData authData);
    }

    interface EngineSettingsCallback {
        void onEngineSettingsUpdated(@Nullable AceStreamPreferences preferences);
    }

    void addPlaybackStateCallback(PlaybackStateCallback cb);
    void removePlaybackStateCallback(PlaybackStateCallback cb);
    void getEngine(@NonNull EngineStateCallback callback);
    void initEngineSession(PlaybackData playbackData, EngineSessionStartListener listener);
    OutputFormat getOutputFormatForContent(String type, String mime, String playerPackageName, boolean isAirCast, boolean isOurPlayer);
}
