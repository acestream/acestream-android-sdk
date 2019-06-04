package org.acestream.sdk;

import com.google.gson.Gson;

import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.errors.TransportFileParsingException;

import androidx.annotation.Keep;

@Keep
public class PlaybackData {
    public OutputFormat outputFormat;
    public TransportFileDescriptor descriptor;
    public MediaFilesResponse.MediaFile mediaFile;
    public String directMediaUrl;
    public int streamIndex = -1;
    public SelectedPlayer selectedPlayer;
    public int allowMultipleThreadsReading = -1;
    public int stopPrevReadThread = -1;
    public boolean disableP2P = false;
    public boolean useFixedSid = false;
    public long seekOnStart = 0;
    public boolean resumePlayback = false;
    public boolean useTimeshift = false;
    public int[] nextFileIndexes = null;
    public boolean keepOriginalSessionInitiator = false;
    public String productKey;

    public static PlaybackData fromJson(String data) {
        return new Gson().fromJson(data, PlaybackData.class);
    }

    public static PlaybackData fromJsonRpcMessage(JsonRpcMessage msg) throws TransportFileParsingException {
        PlaybackData pd = new PlaybackData();

        pd.mediaFile = new MediaFilesResponse.MediaFile();
        pd.mediaFile.type = msg.getString("contentType");
        pd.mediaFile.index = msg.getInt("fileIndex");
        pd.mediaFile.mime = msg.getString("mime");
        pd.mediaFile.size = msg.getLong("videoSize", 0);
        pd.descriptor = TransportFileDescriptor.fromJsonRpcMessage(msg);
        pd.seekOnStart = msg.getLong("seekOnStart", 0);
        pd.streamIndex = msg.getInt("streamIndex", -1);
        pd.selectedPlayer = SelectedPlayer.fromId(msg.getString("selectedPlayer"));
        pd.directMediaUrl = msg.getString("directMediaUrl");

        return pd;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
