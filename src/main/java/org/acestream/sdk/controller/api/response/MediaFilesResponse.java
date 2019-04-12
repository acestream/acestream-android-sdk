package org.acestream.sdk.controller.api.response;

import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.google.gson.Gson;

import org.acestream.sdk.Constants;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class MediaFilesResponse {
    public String transport_file_data;
    public String transport_file_cache_key;
    public String infohash;
    public String name;
    public MediaFile[] files;
    public WrapperData wrapper_data;

    public String toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("infohash", infohash);
            root.put("name", name);
            return root.toString();
        }
        catch(JSONException e) {
            throw new IllegalStateException("failed to serialize MediaFilesResponse", e);
        }
    }

    public static MediaFilesResponse fromJson(String jsonString) throws JSONException {
        MediaFilesResponse response = new MediaFilesResponse();
        JSONObject root = new JSONObject(jsonString);
        response.infohash = root.optString("infohash");
        response.name = root.optString("name");
        return response;
    }

    public String toString() {
        String[] sFiles = new String[files.length];
        int i = 0;
        for(MediaFile f: files) {
            sFiles[i++] = f.toString();
        }
        return String.format(
                Locale.getDefault(),
                "<MediaFilesResponse(count=%d files=%s tf=%s wd=%s)>",
                files.length,
                TextUtils.join(",", sFiles),
                transport_file_data,
                wrapper_data);
    }

    @Nullable
    public MediaFile getMediaFileByIndex(int fileIndex) {
        for(MediaFile mf: files) {
            if(mf.index == fileIndex) {
                return mf;
            }
        }
        return null;
    }

    public static class MediaFile {
        public String infohash;
        public String type;
        public String filename;
        public String mime;
        public String transport_type;
        public int index;
        public long size;

        public static MediaFile fromJson(String data) {
            return new Gson().fromJson(data, MediaFile.class);
        }

        public String toJson() {
            return new Gson().toJson(this);
        }

        public String toString() {
            return String.format(
                    Locale.getDefault(),
                    "<MediaFile(tt=%s ct=%s infohash=%s index=%d mime=%s filename=%s)>",
                    transport_type,
                    type,
                    infohash,
                    index,
                    mime,
                    filename
            );
        }

        public boolean equals(MediaFile other) {
            return other != null
                    && TextUtils.equals(other.infohash, this.infohash)
                    && other.index == this.index;
        }

        public boolean isLive() {
            return TextUtils.equals(type, Constants.CONTENT_TYPE_LIVE);
        }

        public boolean isVideo() {
            return isLive() || !isAudio();
        }

        public boolean isAudio() {
            return mime != null && mime.startsWith("audio/");
        }
    }

    public static class WrapperData {
        public String type;
        public String mime;
        public String data;

        public String toString() {
            return String.format(
                    Locale.getDefault(),
                    "<WrapperData(type=%s mime=%s data=%s)>",
                    type,
                    mime,
                    data
            );
        }
    }
}
