package org.acestream.sdk.controller.api;

import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AceStreamPreferences {
    public final static Set<String> ENGINE_PREFS = new HashSet<>(Arrays.asList(
            "vod_buffer",
            "live_buffer",
            "download_limit",
            "upload_limit",
            "output_format_live",
            "output_format_vod",
            "transcode_audio",
            "transcode_ac3",
            "live_cache_type",
            "vod_cache_type",
            "disk_cache_limit",
            "memory_cache_limit",
            "cache_dir",
            "allow_intranet_access",
            "allow_remote_access",
            "port",
            "max_connections",
            "max_peers"
    ));

    public final static Set<String> INTEGER_PREFS = new HashSet<>(Arrays.asList(
            "vod_buffer",
            "live_buffer",
            "download_limit",
            "upload_limit",
            "disk_cache_limit",
            "memory_cache_limit",
            "port",
            "max_connections",
            "max_peers"
    ));

    private final Bundle mPrefs;

    @Nullable
    public static AceStreamPreferences fromBundle(@Nullable Bundle bundle) {
        if(bundle == null)
            return null;
        return new AceStreamPreferences(bundle);
    }

    public AceStreamPreferences() {
        this(new Bundle());
    }

    public AceStreamPreferences(@NonNull Bundle bundle) {
        //TODO: validate bundle
        mPrefs = bundle;
    }

    public Bundle getAll() {
        return mPrefs;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        return mPrefs.getString(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return mPrefs.getBoolean(key, defaultValue);
    }

    public AceStreamPreferences put(String key, Object value) {
        if(value == null)
            remove(key);
        else if(value instanceof String)
            putString(key, (String)value);
        else if(value instanceof Boolean)
            putBoolean(key, (boolean)value);
        else
            throw new IllegalStateException("String or boolean expected: value=" + value);
        return this;
    }

    public AceStreamPreferences putString(String key, String value) {
        mPrefs.putString(key, value);
        return this;
    }

    public AceStreamPreferences putBoolean(String key, boolean value) {
        mPrefs.putBoolean(key, value);
        return this;
    }

    public void remove(String key) {
        mPrefs.remove(key);
    }

    public SharedPreferences.Editor fill(@NonNull SharedPreferences.Editor editor) {
        for(String key: mPrefs.keySet()) {
            Object value = mPrefs.get(key);
            if(value == null)
                editor.remove(key);
            else if(value instanceof String)
                editor.putString(key, (String)value);
            else if(value instanceof Boolean)
                editor.putBoolean(key, (boolean)value);
            else
                throw new IllegalStateException("String or boolean expected: value=" + value);
        }
        return editor;
    }
}
