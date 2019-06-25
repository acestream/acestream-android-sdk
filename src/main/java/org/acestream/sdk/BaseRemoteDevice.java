package org.acestream.sdk;

import android.net.Uri;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.VlcConstants;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class BaseRemoteDevice implements IRemoteDevice {
    protected final static String TAG = "AS/RD";

    // Hardcoded debug flag
    private final static boolean LOG_STATUS_MESSAGES = false;

    @SuppressWarnings("WeakerAccess")
    public class Messages {
        public final static String PLAYER_STATUS = "playerStatus";
        public final static String ENGINE_STATUS = "engineStatus";
        public final static String PLAYER_PLAYING = "playerPlaying";
        public final static String PLAYER_STOPPED = "playerStopped";
        public final static String PLAYER_END_REACHED = "playerEndReached";
        public final static String PLAYER_CLOSED = "playerClosed";
        public final static String PLAYER_PAUSED = "playerPaused";
        public final static String PLAYER_BUFFERING = "playerBuffering";
        public final static String PLAYBACK_STARTED = "playbackStarted";
        public final static String PLAYBACK_START_FAILED = "playbackStartFailed";
        public final static String ENGINE_SESSION_STARTED = "engineSessionStarted";
        public final static String ENGINE_SESSION_STOPPED = "engineSessionStopped";
        public final static String PLAYER_OPENING = "playerOpening";
        public final static String PLAYER_ERROR = "playerError";
        public final static String PLAYER_PAUSABLE_CHANGED = "playerPausableChanged";
        public final static String PLAYER_TIME_CHANGED = "playerTimeChanged";
        public final static String PLAYER_LENGTH_CHANGED = "playerLengthChanged";
        public final static String PLAYER_VOLUME_CHANGED = "playerVolumeChanged";
        public final static String PLAYER_VIDEO_SIZE_CHANGED = "playerVideoSizeChanged";
        public final static String PLAYER_DEINTERLACE_MODE_CHANGED = "playerDeinterlaceModeChanged";
        public final static String PLAYER_AUDIO_TRACKS_CHANGED = "playerAudioTracksChanged";
        public final static String PLAYER_SUBTITLE_TRACKS_CHANGED = "playerSubtitleTracksChanged";
        public final static String PLAYER_AUDIO_OUTPUT_CHANGED = "playerAudioOutputChanged";
        public final static String PLAYER_AUDIO_DIGITAL_OUTPUT_CHANGED = "playerAudioDigitalOutputChanged";
    }

    protected String mId;
    protected String mName;
    protected String mIpAddress;
    protected SelectedPlayer mSelectedPlayer = null;
    protected String mOutputFormat = null;
    protected PlayerState mPlayerState = new PlayerState();

    public String getId() {
        return mId;
    }

    public String getIpAddress() {
        return mIpAddress;
    }

    public String getName() {
        return mName;
    }

    protected boolean setSelectedPlayer(SelectedPlayer player) {
        if(!SelectedPlayer.equals(mSelectedPlayer, player)) {
            mSelectedPlayer = player;
            return true;
        }
        return false;
    }

    protected boolean setOutputFormat(String outputFormat) {
        if(!TextUtils.equals(mOutputFormat, outputFormat)) {
            mOutputFormat = outputFormat;
            return true;
        }
        return false;
    }

    protected List<JsonRpcMessage> handleMessage(@NonNull JsonRpcMessage msg, boolean generateExtraMessages) {
        if(LOG_STATUS_MESSAGES || !TextUtils.equals(msg.getMethod(), Messages.ENGINE_STATUS) && !TextUtils.equals(msg.getMethod(), Messages.PLAYER_STATUS)) {
            Log.v(TAG, "handleMessage: msg=" + msg.toString() + " device=" + this);
        }

        // Secondary message can be generated here (we fire events on state change).
        List<JsonRpcMessage> extraMessages = new ArrayList<>();

        switch(msg.getMethod()) {
            case Messages.PLAYER_PLAYING:
                mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                break;
            case Messages.PLAYER_PAUSED:
                mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                break;
            case Messages.PLAYER_STOPPED:
                mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
            case Messages.PLAYER_STATUS:
                int state = msg.getInt("state");
                boolean stateChanged = mPlayerState.setState(state);
                switch(state) {
                    case VlcConstants.VlcState.OPENING:
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                        if(generateExtraMessages && stateChanged) {
                            extraMessages.add(new JsonRpcMessage(Messages.PLAYER_OPENING));
                        }
                        break;
                    case VlcConstants.VlcState.PLAYING:
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                        if(generateExtraMessages && stateChanged) {
                            extraMessages.add(new JsonRpcMessage(Messages.PLAYER_PLAYING));
                            extraMessages.add(new JsonRpcMessage(Messages.PLAYER_PAUSABLE_CHANGED, "value", true));
                        }
                        break;
                    case VlcConstants.VlcState.PAUSED:
                        if(generateExtraMessages && stateChanged) {
                            extraMessages.add(new JsonRpcMessage(Messages.PLAYER_PAUSED));
                        }
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                        break;
                    case VlcConstants.VlcState.STOPPING:
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                        break;
                    case VlcConstants.VlcState.IDLE:
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                        break;
                    case VlcConstants.VlcState.ERROR:
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                        if(generateExtraMessages && stateChanged) {
                            extraMessages.add(new JsonRpcMessage(Messages.PLAYER_ERROR));
                        }
                        break;
                    case VlcConstants.VlcState.ENDED:
                        mPlayerState.setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                        break;
                }

                //TODO: whether methods are called when generateExtraMessages=false
                if(mPlayerState.setTime(msg.getLong("time")) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_TIME_CHANGED, "value", mPlayerState.getTime()));
                }
                if(mPlayerState.setLength(msg.getLong("duration")) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_LENGTH_CHANGED, "value", mPlayerState.getLength()));
                }
                if(mPlayerState.setVolume(msg.getInt("volume")) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_VOLUME_CHANGED, "value", mPlayerState.getVolume()));
                }
                if(mPlayerState.setVideoSize(msg.getInt("videoSize")) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_VIDEO_SIZE_CHANGED, "value", mPlayerState.getVideoSize()));
                }
                if(mPlayerState.setDeinterlaceMode(msg.getString("deinterlaceMode")) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_DEINTERLACE_MODE_CHANGED, "value", mPlayerState.getDeinterlaceMode()));
                }
                if(mPlayerState.setAudioTracks(msg) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_AUDIO_TRACKS_CHANGED));
                }
                if(mPlayerState.setSubtitleTracks(msg) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_SUBTITLE_TRACKS_CHANGED));
                }
                if(mPlayerState.setAudioDigitalOutputEnabled(msg) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_AUDIO_DIGITAL_OUTPUT_CHANGED));
                }
                if(mPlayerState.setAudioOutput(msg) && generateExtraMessages) {
                    extraMessages.add(new JsonRpcMessage(Messages.PLAYER_AUDIO_OUTPUT_CHANGED));
                }

                break;
            case Messages.PLAYBACK_STARTED:
                setSelectedPlayer(SelectedPlayer.fromId(msg.getString("selectedPlayer")));
                break;
            case Messages.ENGINE_STATUS:
                setSelectedPlayer(SelectedPlayer.fromId(msg.getString("selectedPlayer")));
                setOutputFormat(msg.getString("outputFormat"));
                break;
        }

        return extraMessages;
    }

    public int getState() {
        return mPlayerState.getState();
    }

    public String getDeinterlaceMode() {
        return mPlayerState.getDeinterlaceMode();
    }

    public int getVideoSize() {
        return mPlayerState.getVideoSize();
    }

    public int getVolume() {
        return mPlayerState.getVolume();
    }

    public long getTime() {
        return mPlayerState.getTime();
    }

    public int getPlaybackState() {
        return mPlayerState.getPlaybackState();
    }

    public float getPosition() {
        if(mPlayerState.getLength() > 0) {
            return (float)(mPlayerState.getTime()) / mPlayerState.getLength();
        }
        else {
            return 0;
        }
    }

    public long getLength() {
        return mPlayerState.getLength();
    }

    public boolean isPlaying() {
        return mPlayerState.isPlaying();
    }

    public boolean isPaused() {
        return mPlayerState.isPaused();
    }

    public boolean isSeekable() {
        return mPlayerState.isSeekable();
    }

    public boolean isPausable() {
        return mPlayerState.isPausable();
    }

    public int getAudioTracksCount() {
        return mPlayerState.getAudioTracksCount();
    }

    public TrackDescription[] getAudioTracks() {
        return mPlayerState.getAudioTracks();
    }

    public int getAudioTrack() {
        return mPlayerState.getAudioTrack();
    }

    public int getSpuTracksCount() {
        return mPlayerState.getSpuTracksCount();
    }

    public TrackDescription[] getSpuTracks() {
        return mPlayerState.getSpuTracks();
    }

    public int getSpuTrack() {
        return mPlayerState.getSpuTrack();
    }

    public SelectedPlayer getSelectedPlayer() {
        return mSelectedPlayer;
    }

    public boolean isOurPlayer() {
        return mSelectedPlayer != null && mSelectedPlayer.isOurPlayer();
    }

    public String getOutputFormat() {
        return mOutputFormat;
    }

    public void stop() {
        stop(false);
    }

    public boolean isVideoPlaying() {
        // this means that video is not playing in local player
        return false;
    }

    public long getAudioDelay() {
        // not implemented
        return 0;
    }

    public long getSpuDelay() {
        // not implemented
        return 0;
    }

    public float getRate() {
        // not implemented
        return 1.0f;
    }

    public void setRate(float rate, boolean save) {
        // not implemented
    }

    public void navigate(int where) {
        // not implemented
    }

    public int getChapterIdx() {
        // not implemented
        return 0;
    }

    public void setChapterIdx(int chapter) {
        // not implemented
    }

    public int getTitleIdx() {
        // not implemented
        return 0;
    }

    public void setTitleIdx(int title) {
        // not implemented
    }

    public boolean updateViewpoint(float yaw, float pitch, float roll, float fov, boolean absolute) {
        // not implemented
        return true;
    }

    public boolean setVideoTrack(int index) {
        // not implemented
        return true;
    }

    public int getVideoTrack() {
        // not implemented
        return 0;
    }

    public boolean addSubtitleTrack(String path, boolean select) {
        // not implemented
        return true;
    }

    public boolean addSubtitleTrack(Uri uri, boolean select) {
        // not implemented
        return true;
    }

    public boolean setAudioDelay(long delay) {
        // not implemented
        return true;
    }

    public boolean setSpuDelay(long delay) {
        // not implemented
        return true;
    }

    public void setVideoScale(float scale) {
        // not implemented
    }

    public void setVideoAspectRatio(@Nullable String aspect) {
        // not implemented
    }
}
