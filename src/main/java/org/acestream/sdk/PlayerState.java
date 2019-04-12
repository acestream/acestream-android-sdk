package org.acestream.sdk;

import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.sdk.csdk.PlaybackStatus;
import org.acestream.sdk.utils.VlcConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PlayerState {
    private final static String TAG = "AS/PlayerState";

    private int mState;
    private int mPlaybackState; // android.support.v4.media.session.PlaybackStateCompat
    private int mVideoSize;
    private long mLength;
    private long mTime;
    private int mVolume;
    private int mCurrentAudioTrack;
    private List<TrackDescription> mAudioTracks = new ArrayList<>();
    private int mCurrentSpuTrack;
    private List<TrackDescription> mSpuTracks = new ArrayList<>();
    private String mDeinterlaceMode;
    private boolean mAudioDigitalOutputEnabled;
    private String mAout;

    public PlayerState() {
        reset();
    }

    public int getState() {
        return mState;
    }

    public int getPlaybackState() {
        return mPlaybackState;
    }

    public boolean isPlaying() {
        return getPlaybackState() == PlaybackStateCompat.STATE_PLAYING;
    }

    public boolean isPausable() {
        return true;
    }

    public boolean isSeekable() {
        return true;
    }

    public long getTime() {
        return mTime;
    }

    public long getLength() {
        return mLength;
    }

    public int getVolume() {
        return mVolume;
    }

    public int getVideoSize() {
        return mVideoSize;
    }

    public String getDeinterlaceMode() {
        return mDeinterlaceMode;
    }

    public int getAudioTracksCount() {
        return mAudioTracks.size();
    }

    public TrackDescription[] getAudioTracks() {
        return mAudioTracks.toArray(new TrackDescription[0]);
    }

    public int getAudioTrack() {
        return mCurrentAudioTrack;
    }

    public int getSpuTracksCount() {
        return mSpuTracks.size();
    }

    public TrackDescription[] getSpuTracks() {
        return mSpuTracks.toArray(new TrackDescription[0]);
    }

    public int getSpuTrack() {
        return mCurrentSpuTrack;
    }

    public boolean setDeinterlaceMode(String value) {
        if(!TextUtils.equals(value, mDeinterlaceMode)) {
            mDeinterlaceMode = value;
            return true;
        }
        return false;
    }

    public void setCsdkPlaybackStatus(int status) {
        int vlcState;
        int compatState;
        switch(status) {
            case PlaybackStatus.UNKNOWN:
                vlcState = VlcConstants.VlcState.IDLE;
                compatState = PlaybackStateCompat.STATE_NONE;
                break;
            case PlaybackStatus.IDLE:
                vlcState = VlcConstants.VlcState.IDLE;
                compatState = PlaybackStateCompat.STATE_STOPPED;
                break;
            case PlaybackStatus.PLAYING:
                vlcState = VlcConstants.VlcState.PLAYING;
                compatState = PlaybackStateCompat.STATE_PLAYING;
                break;
            case PlaybackStatus.PAUSED:
                vlcState = VlcConstants.VlcState.PAUSED;
                compatState = PlaybackStateCompat.STATE_PAUSED;
                break;
            case PlaybackStatus.BUFFERING:
                vlcState = VlcConstants.VlcState.PLAYING;
                compatState = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case PlaybackStatus.FINISHED:
                vlcState = VlcConstants.VlcState.ENDED;
                compatState = PlaybackStateCompat.STATE_STOPPED;
                break;
            default:
                vlcState = VlcConstants.VlcState.IDLE;
                compatState = PlaybackStateCompat.STATE_NONE;
        }

        setState(vlcState);
        setPlaybackState(compatState);
    }

    public boolean setState(int value) {
        if(value != mState) {
            mState = value;
            return true;
        }
        return false;
    }

    public boolean setPlaybackState(int value) {
        if(value != mPlaybackState) {
            mPlaybackState = value;
            return true;
        }
        return false;
    }

    public boolean setVideoSize(int value) {
        if(value != mVideoSize) {
            mVideoSize = value;
            return true;
        }
        return false;
    }

    public boolean setLength(long value) {
        if(value != mLength) {
            mLength = value;
            return true;
        }
        return false;
    }

    public boolean setTime(long value) {
        if(value != mTime) {
            mTime = value;
            return true;
        }
        return false;
    }

    public boolean setVolume(int value) {
        if(value != mVolume) {
            mVolume = value;
            return true;
        }
        return false;
    }

    public boolean setAudioDigitalOutputEnabled(JsonRpcMessage msg) {
        boolean value = msg.getBoolean("audioDigitalOutputEnabled");
        if(value != mAudioDigitalOutputEnabled) {
            mAudioDigitalOutputEnabled = value;
            return true;
        }
        return false;
    }

    public boolean setAudioOutput(JsonRpcMessage msg) {
        String aout = msg.getString("aout");
        if(!TextUtils.equals(aout, mAout)) {
            mAout = aout;
            return true;
        }
        return false;
    }

    public void reset() {
        mState = VlcConstants.VlcState.IDLE;
        mPlaybackState = PlaybackStateCompat.STATE_STOPPED;
        mVideoSize = -1;
        mLength = -1;
        mTime = -1;
        mVolume = -1;
        mCurrentAudioTrack = -1;
        mAudioTracks.clear();
        mCurrentSpuTrack = -1;
        mSpuTracks.clear();
        mDeinterlaceMode = Constants.DEINTERLACE_MODE_DISABLED;
        mAudioDigitalOutputEnabled = false;
        mAout = null;
    }

    public boolean setAudioTracks(JsonRpcMessage msg) {
        boolean changed = false;
        JSONArray jsonTracks = msg.getJSONArray("audioTracks");
        if (jsonTracks != null) {
            int selectedTrack = msg.getInt("selectedAudioTrack", -1);
            if(selectedTrack != mCurrentAudioTrack) {
                mCurrentAudioTrack = selectedTrack;
                changed = true;
            }

            if(jsonTracks.length() != mAudioTracks.size()) {
                changed = true;
                mAudioTracks.clear();
                for (int i = 0; i < jsonTracks.length(); i++) {
                    try {
                        JSONObject jsonTrack = jsonTracks.getJSONObject(i);
                        mAudioTracks.add(new TrackDescription(
                                jsonTrack.getInt("id"),
                                jsonTrack.getString("name")));
                    }
                    catch(JSONException e) {
                        Log.e(TAG, "failed to parse audio track: " + e.getMessage());
                    }
                }
            }
        }
        return changed;
    }

    public boolean setSubtitleTracks(JsonRpcMessage msg) {
        boolean changed = false;
        JSONArray jsonTracks = msg.getJSONArray("subtitleTracks");
        if (jsonTracks != null) {
            int selectedTrack = msg.getInt("selectedSubtitleTrack", -1);
            if(selectedTrack != mCurrentSpuTrack) {
                mCurrentSpuTrack = selectedTrack;
                changed = true;
            }

            if(jsonTracks.length() != mSpuTracks.size()) {
                changed = true;
                mSpuTracks.clear();
                for (int i = 0; i < jsonTracks.length(); i++) {
                    try {
                        JSONObject jsonTrack = jsonTracks.getJSONObject(i);
                        mSpuTracks.add(new TrackDescription(
                                jsonTrack.getInt("id"),
                                jsonTrack.getString("name")));
                    }
                    catch(JSONException e) {
                        Log.e(TAG, "failed to parse subtitle track: " + e.getMessage());
                    }
                }
            }
        }
        return changed;
    }
}