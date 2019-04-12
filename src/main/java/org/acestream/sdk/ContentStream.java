package org.acestream.sdk;

import android.text.TextUtils;

public class ContentStream {
    public int index;
    public String name;
    public int quality;
    public int bitrate;
    public int streamType = ContentType.UNKNOWN;
    public int contentType = ContentType.UNKNOWN;
    public String codecs;
    public int bandwidth;
    public String resolution;

    public static class ContentType {
        public static final int UNKNOWN = 0;
        public static final int AUDIO = 1;
        public static final int VIDEO = 2;
    }

    public static class StreamType {
        public static final int UNKNOWN = 0;
        public static final int DIRECT = 1;
        public static final int HLS = 2;
    }

    public String getName() {
        if(!TextUtils.isEmpty(name)) {
            return name;
        }

        if(bandwidth > 0) {
            int kbps = bandwidth / 1000;
            return kbps + " kbps";
        }
        else if(bitrate > 0) {
            int kbps = bitrate * 8 / 1000;
            return kbps + " kbps";
        }

        //TODO: handle audio
        return "Stream " + index;
    }

    @Override
    public String toString() {
        return "ContentStream(name=" + name + " bandwidth=" + bandwidth + ")";
    }
}
