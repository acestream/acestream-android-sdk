package org.acestream.sdk;

import com.google.gson.Gson;

import org.acestream.sdk.controller.api.response.VastTag;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

@Keep
public class EngineSession {
    public String playbackSessionId = null;
    public String playbackUrl = null;
    public String statUrl = null;
    public String commandUrl = null;
    public String eventUrl = null;
    public String infohash = null;
    public VastTag[] vastTags = null;
    public PlaybackData playbackData;
    public long startedAt;
    public boolean isDirect = false;
    public int isLive = -1;

    public static EngineSession fromJson(@Nullable String json) {
        if(json == null) return null;
        return new Gson().fromJson(json, EngineSession.class);
    }

    public static String toJson(@Nullable EngineSession session) {
        return (session == null) ? null : session.toJson();
    }

    public EngineSession() {
        startedAt = System.currentTimeMillis();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    @Override
    public String toString() {
        return String.format("outputFormat=%s playback=%s stat=%s cmd=%s event=%s", playbackData.outputFormat, playbackUrl, statUrl, commandUrl, eventUrl);
    }
}
