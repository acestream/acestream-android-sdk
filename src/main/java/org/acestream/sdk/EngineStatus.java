package org.acestream.sdk;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EngineStatus {
    private final static String TAG = "AceStream/EngStatus";

    public static class LivePosition {
        public int first;
        public int last;
        public int pos;
        public int firstTimestamp;
        public int lastTimestamp;
        public boolean isLive;
        public int bufferPieces;

        public String toString() {
            return String.format("LivePosition(%d/%d-%d/%d-%d, live=%d)",
                    pos,
                    first,
                    last,
                    firstTimestamp,
                    lastTimestamp,
                    isLive ? 1 : 0
                    );
        }
    }

    public String status;
    public String playbackSessionId;
    public int progress;
    public int peers;
    public int speedDown;
    public int speedUp;
    public LivePosition livePos;
    public String errorMessage;
    public List<ContentStream> streams;
    public int currentStreamIndex;
    public int isLive = -1;
    public SelectedPlayer selectedPlayer = null;
    public SystemUsageInfo systemInfo = null;
    public String outputFormat = null;
    public int fileIndex = -1;
    public int debugLevel = 0;

    // internal session: used in tests
    public int initiatorType = -1;
    public String initiatorId = null;
    public String contentKey = null;
    public int isOurPlayer = -1;

    public static EngineStatus error(String errorMessage) {
        EngineStatus status = new EngineStatus();
        status.status = "error";
        status.errorMessage = errorMessage;
        return status;
    }

    public static EngineStatus fromString(String statusString) {
        EngineStatus status = new EngineStatus();
        status.status = statusString;
        return status;
    }

    public EngineStatus() {
        streams = new ArrayList<>();
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();

            root.put("status", status);
            root.put("playbackSessionId", playbackSessionId);
            root.put("progress", progress);
            root.put("peers", peers);
            root.put("speedDown", speedDown);
            root.put("speedUp", speedUp);
            root.put("errorMessage", errorMessage);
            root.put("currentStreamIndex", currentStreamIndex);
            root.put("isLive", isLive);

            // add optional
            if(outputFormat != null) {
                root.put("outputFormat", outputFormat);
            }
            if(fileIndex != -1) {
                root.put("fileIndex", fileIndex);
            }
            if(selectedPlayer != null) {
                root.put("selectedPlayer", selectedPlayer.toJson());
            }
            if(systemInfo != null) {
                root.put("systemInfo", systemInfo.toJson());
            }

            if(livePos != null) {
                JSONObject livePosition = new JSONObject();
                livePosition.put("first", livePos.first);
                livePosition.put("last", livePos.last);
                livePosition.put("firstTimestamp", livePos.firstTimestamp);
                livePosition.put("lastTimestamp", livePos.lastTimestamp);
                livePosition.put("pos", livePos.pos);
                livePosition.put("isLive", livePos.isLive);
                livePosition.put("bufferPieces", livePos.bufferPieces);
                root.put("livePosition", livePosition);
            }

            if(streams.size() > 0) {
                JSONArray jsonStreams = new JSONArray();
                for (ContentStream stream : streams) {
                    JSONObject jsonStream = new JSONObject();
                    jsonStream.put("index", stream.index);
                    jsonStream.put("streamType", stream.streamType);
                    jsonStream.put("contentType", stream.contentType);
                    jsonStream.put("name", stream.name);
                    jsonStream.put("quality", stream.quality);
                    jsonStream.put("bitrate", stream.bitrate);
                    jsonStream.put("bandwidth", stream.bandwidth);
                    jsonStream.put("codecs", stream.codecs);
                    jsonStream.put("resolution", stream.resolution);
                    jsonStreams.put(jsonStream);
                }
                root.put("streams", jsonStreams);
            }

            return root.toString();
        }
        catch(JSONException e) {
            Log.e(TAG, "failed to serialize engine status", e);
            return null;
        }
    }

    public static EngineStatus fromJson(String data) {
        try {
            EngineStatus engineStatus = new EngineStatus();

            JSONObject root = new JSONObject(data);

            engineStatus.status = root.getString("status");
            engineStatus.playbackSessionId = root.getString("playbackSessionId");
            engineStatus.progress = root.getInt("progress");
            engineStatus.peers = root.getInt("peers");
            engineStatus.speedDown = root.getInt("speedDown");
            engineStatus.speedUp = root.getInt("speedUp");
            engineStatus.errorMessage = root.getString("errorMessage");
            engineStatus.currentStreamIndex = root.getInt("currentStreamIndex");
            engineStatus.isLive = root.getInt("isLive");

            // parse optional
            engineStatus.outputFormat = root.optString("outputFormat");
            engineStatus.fileIndex = root.optInt("fileIndex", -1);
            if(root.has("selectedPlayer")) {
                try {
                    engineStatus.selectedPlayer = SelectedPlayer.fromJson(root.getString("selectedPlayer"));
                }
                catch(JSONException e) {
                    Log.e(TAG, "Failed to deserialize player", e);
                }
            }
            if(root.has("systemInfo")) {
                engineStatus.systemInfo = SystemUsageInfo.fromJson(root.getString("systemInfo"));
            }

            if(root.has("livePosition")) {
                JSONObject livePosition = root.getJSONObject("livePosition");
                engineStatus.livePos = new LivePosition();
                engineStatus.livePos.first = livePosition.getInt("first");
                engineStatus.livePos.last = livePosition.getInt("last");
                engineStatus.livePos.firstTimestamp = livePosition.getInt("firstTimestamp");
                engineStatus.livePos.lastTimestamp = livePosition.getInt("lastTimestamp");
                engineStatus.livePos.pos = livePosition.getInt("pos");
                engineStatus.livePos.isLive = livePosition.getBoolean("isLive");
                engineStatus.livePos.bufferPieces = livePosition.getInt("bufferPieces");
            }
            else {
                engineStatus.livePos = null;
            }

            if(root.has("streams")) {
                JSONArray streams = root.getJSONArray("streams");
                for(int i = 0; i < streams.length(); i++) {
                    JSONObject jsonStream = streams.getJSONObject(i);
                    ContentStream stream = new ContentStream();
                    stream.index = jsonStream.getInt("index");
                    stream.streamType = jsonStream.getInt("streamType");
                    stream.contentType = jsonStream.getInt("contentType");
                    stream.name = jsonStream.optString("name", null);
                    stream.quality = jsonStream.optInt("quality", 0);
                    stream.bitrate = jsonStream.optInt("bitrate", 0);
                    stream.bandwidth = jsonStream.optInt("bandwidth", 0);
                    stream.codecs = jsonStream.optString("codecs", null);
                    stream.resolution = jsonStream.optString("resolution", null);

                    if(jsonStream.has("type")) {
                        switch(jsonStream.getString("type")) {
                            case "audio":
                                stream.contentType = ContentStream.ContentType.AUDIO;
                                break;
                            case "video":
                                stream.contentType = ContentStream.ContentType.VIDEO;
                                break;
                        }
                    }

                    engineStatus.streams.add(stream);
                }
            }

            return engineStatus;
        }
        catch(JSONException e) {
            Log.e(TAG, "failed to deserialize engine status", e);
            return null;
        }
    }

    public String toString() {
        return String.format(Locale.getDefault(), "<EngineStatus(s=%s peers=%d dl=%d ul=%d)>",
                status,
                peers,
                speedDown,
                speedUp);
    }
}
