package org.acestream.sdk.utils;

public class VlcConstants {
    public static final int SURFACE_BEST_FIT = 0;
    public static final int SURFACE_FIT_SCREEN = 1;
    public static final int SURFACE_FILL = 2;
    public static final int SURFACE_16_9 = 3;
    public static final int SURFACE_4_3 = 4;
    public static final int SURFACE_ORIGINAL = 5;

    public static final int HW_ACCELERATION_AUTOMATIC = -1;
    public static final int HW_ACCELERATION_DISABLED = 0;
    public static final int HW_ACCELERATION_DECODING = 1;
    public static final int HW_ACCELERATION_FULL = 2;

    public static class VlcState {
        public static final int IDLE = 0;
        public static final int OPENING = 1;
        public static final int PLAYING = 3;
        public static final int PAUSED = 4;
        public static final int STOPPING = 5;
        public static final int ENDED = 6;
        public static final int ERROR = 7;
    }
}
