package org.acestream.sdk;

import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;

import org.acestream.sdk.interfaces.ConnectableDeviceListener;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.utils.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class RemoteDevice extends BaseRemoteDevice {
    private boolean mIsAceCast;
    private AceStreamManager mManager;
    private boolean mConnected = false;

    public static RemoteDevice fromJson(AceStreamManager manager, String json) {
        try {
            JSONObject root = new JSONObject(json);
            return new RemoteDevice(
                    manager,
                    root.getString("id"),
                    root.getString("name"),
                    root.getString("ipAddress"),
                    root.getBoolean("isAceCast"));
        }
        catch(JSONException e) {
            throw new IllegalStateException("Failed to deserialize remote device object", e);
        }
    }

    private RemoteDevice(AceStreamManager manager, String id, String name, String ipAddress, boolean isAceCast) {
        mManager = manager;
        mId = id;
        mName = name;
        mIpAddress = ipAddress;
        mIsAceCast = isAceCast;
    }

    // AceCast callbacks
    protected void onMessage(@NonNull JsonRpcMessage msg) {
        handleMessage(msg, false);
    }

    protected void onConnected() {
        Logger.vv(TAG, "onConnected");
        mConnected = true;
    }

    protected void onDisconnected() {
        Logger.vv(TAG, "onDisconnected");
        mConnected = false;
    }

    // CSDK callbacks
    //TODO: connected, disconnected
    private ConnectableDeviceListener mCsdkListener = new ConnectableDeviceListener() {
        @Override
        public void onStatus(IRemoteDevice device, int status) {
            mPlayerState.setCsdkPlaybackStatus(status);
        }

        @Override
        public void onPosition(IRemoteDevice device, Long position) {
            mPlayerState.setTime(position);
        }

        @Override
        public void onDuration(IRemoteDevice device, Long duration) {
            mPlayerState.setLength(duration);
        }

        @Override
        public void onVolume(IRemoteDevice device, Float volume) {
            mPlayerState.setVolume(Math.round(volume * 100));
        }
    };

    public ConnectableDeviceListener getCsdkListener() {
        return mCsdkListener;
    }

    private Message obtainMessage(int what) {
        return obtainMessage(what, null);
    }

    private Message obtainMessage(int what, @Nullable Bundle data) {
        Message msg = mManager.obtainMessage(what);
        if(data == null) {
            data = new Bundle();
        }
        data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE_ID, getId());
        data.putBoolean(AceStreamManager.MSG_PARAM_IS_ACECAST, isAceCast());
        msg.setData(data);
        return msg;
    }

    public boolean equals(SelectedPlayer player) {
        if(player == null)
            return false;
        else if(player.type == SelectedPlayer.ACESTREAM_DEVICE)
            return isAceCast() && TextUtils.equals(getId(), player.id1);
        else if(player.type == SelectedPlayer.CONNECTABLE_DEVICE)
            return !isAceCast() && TextUtils.equals(getId(), player.id1);
        else
            return false;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(),"<RD: id=%s name=%s>", mId, mName);
    }

    @Override
    public boolean isAceCast() {
        return mIsAceCast;
    }

    public int getDeviceType() {
        return mIsAceCast ? SelectedPlayer.ACESTREAM_DEVICE : SelectedPlayer.CONNECTABLE_DEVICE;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public void play() {
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_PLAY));
    }

    public void pause() {
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_PAUSE));
    }

    public void stop(boolean disconnect) {
        Bundle data = new Bundle();
        data.putBoolean(AceStreamManager.MSG_PARAM_DISCONNECT, disconnect);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_STOP, data));
    }

    public void setTime(long time) {
        Bundle data = new Bundle();
        data.putLong(AceStreamManager.MSG_PARAM_TIME, time);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_TIME, data));
    }

    public int setVolume(int volume) {
        Bundle data = new Bundle();
        data.putInt(AceStreamManager.MSG_PARAM_VOLUME, volume);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_VOLUME, data));
        return volume;
    }

    public void setPosition(float position) {
        Bundle data = new Bundle();
        data.putFloat(AceStreamManager.MSG_PARAM_POSITION, position);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_POSITION, data));
    }

    public boolean setAudioTrack(int track) {
        Bundle data = new Bundle();
        data.putInt(AceStreamManager.MSG_PARAM_TRACK, track);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_AUDIO_TRACK, data));
        return true;
    }

    public boolean setSpuTrack(int track) {
        Bundle data = new Bundle();
        data.putInt(AceStreamManager.MSG_PARAM_TRACK, track);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_SPU_TRACK, data));
        return true;
    }

    public void setVideoSize(String size) {
        Bundle data = new Bundle();
        data.putString(AceStreamManager.MSG_PARAM_VIDEO_SIZE, size);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_VIDEO_SIZE, data));
    }

    public boolean setAudioDigitalOutputEnabled(boolean enabled) {
        Bundle data = new Bundle();
        data.putBoolean(AceStreamManager.MSG_PARAM_ENABLED, enabled);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_AUDIO_DIGITAL_OUTPUT_ENABLED, data));
        return true;
    }

    public boolean setAudioOutput(String aout) {
        Bundle data = new Bundle();
        data.putString(AceStreamManager.MSG_PARAM_AOUT, aout);
        mManager.sendMessage(obtainMessage(AceStreamManager.MSG_DEVICE_SET_AUDIO_OUTPUT, data));
        return true;
    }
}
