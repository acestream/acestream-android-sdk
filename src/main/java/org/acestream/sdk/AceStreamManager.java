package org.acestream.sdk;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import org.acestream.engine.ServiceClient;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.DeviceDiscoveryListener;
import org.acestream.sdk.interfaces.EngineCallbackListener;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.interfaces.ConnectableDeviceListener;
import org.acestream.sdk.interfaces.RemoteDeviceListener;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.RunnableWithParams;
import org.acestream.sdk.utils.Workers;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.acestream.sdk.Constants.CONTENT_TYPE_VOD;
import static org.acestream.sdk.Constants.MIME_HLS;
import static org.acestream.sdk.Constants.PREF_KEY_SHOW_DEBUG_INFO;

@SuppressWarnings({"WeakerAccess", "unused"})
public class AceStreamManager extends Service implements IAceStreamManager, ServiceClient.Callback {

    private final static String TAG = "AS/Manager";

    // static instance hack
    private static AceStreamManager sInstance = null;

    // listeners and callbacks
    private final Set<EngineStatusListener> mEngineStatusListeners = new CopyOnWriteArraySet<>();
    private final Set<DeviceDiscoveryListener> mDeviceDiscoveryListeners = new CopyOnWriteArraySet<>();
    private final Set<RemoteDeviceListener> mRemoteDeviceListeners = new CopyOnWriteArraySet<>();
    private final Set<ConnectableDeviceListener> mPlaybackStatusListeners = new CopyOnWriteArraySet<>();
    private final Set<PlaybackStateCallback> mPlaybackStateCallbacks = new CopyOnWriteArraySet<>();
    private final Set<EngineCallbackListener> mEngineCallbackListeners = new CopyOnWriteArraySet<>();
    private final Set<Callback> mCallbacks = new CopyOnWriteArraySet<>();
    private Set<EngineStateCallback> mEngineStateCallbacks = new CopyOnWriteArraySet<>();
    private final List<EngineSettingsCallback> mEngineSettingsCallbacks = new ArrayList<>();
    private final List<AuthCallback> mAuthCallbacks = new ArrayList<>();

    // remote client
    private final RemoteClient mRemoteClient = new RemoteClient(this, new RemoteClient.Callback() {
        @Override
        public void onConnected(Messenger remoteMessenger) {
            Logger.v(TAG, "remote service connected");
            mRemoteMessenger = remoteMessenger;
            register();
        }

        @Override
        public void onDisconnected() {
            Logger.v(TAG, "remote service disconnected");
            mRemoteMessenger = null;
            mReady = false;
        }
    });

    // private fields
    private boolean mReady = false;
    private boolean mBonusAdsAvailable = false;
    private final Handler mClientMessengerHandler = new ClientMessengerHandler();
    private final Messenger mClientMessenger = new Messenger(mClientMessengerHandler);
    private Messenger mRemoteMessenger = null;
    private ServiceClient mEngineServiceClient = null;
    private EngineApi mEngineApi = null;
    private AuthData mCurrentAuthData = null;
    private Map<String,RemoteDevice> mRemoteDevices = new HashMap<>();
    private SparseArray<CastResultListener> mCastResultListeners = new SparseArray<>();
    private SparseArray<EngineSessionStartListener> mEngineSessionStartListeners = new SparseArray<>();
    private EngineSession mEngineSession = null;
    protected CastResultListener mCastResultListener = null;
    private final List<Runnable> mOnReadyQueue = new CopyOnWriteArrayList<>();
    private final List<org.acestream.engine.controller.Callback<AceStreamPreferences>> mOnEngineSettingsQueue = new CopyOnWriteArrayList<>();
    private AceStreamPreferences mAceStreamPreferences = new AceStreamPreferences();

    // binder
    private IBinder mLocalBinder = new LocalBinder();

    private class LocalBinder extends Binder {
        AceStreamManager getService() {
            return AceStreamManager.this;
        }
    }

    public static AceStreamManager getService(IBinder iBinder) {
        final AceStreamManager.LocalBinder binder = (AceStreamManager.LocalBinder) iBinder;
        return binder.getService();
    }

    // broadcast receiver
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent == null) return;
            if(TextUtils.equals(intent.getAction(), AceStream.ACTION_STOP_APP)) {
                Log.d(TAG, "receiver: stop app");
                stopSelf();
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // interfaces
    @SuppressWarnings("unused")
    public interface CastResultListener {
        void onSuccess();
        void onSuccess(RemoteDevice device, SelectedPlayer selectedPlayer);
        void onError(String error);
        void onDeviceConnected(RemoteDevice device);
        void onDeviceDisconnected(RemoteDevice device);
        void onCancel();
        boolean isWaiting();
    }

    public interface Callback {
        void onEngineConnected(EngineApi service);
        void onEngineFailed();
        void onEngineUnpacking();
        void onEngineStarting();
        void onEngineStopped();
        void onBonusAdsAvailable(boolean available);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Service lifecycle
    @Override
    public void onCreate() {
        Logger.vv(TAG, "onCreate");
        super.onCreate();

        sInstance = this;

        final IntentFilter filter = new IntentFilter();
        filter.addAction(AceStream.ACTION_STOP_APP);
        registerReceiver(mBroadcastReceiver, filter);

        mRemoteClient.connect();
    }

    @Override
    public void onDestroy() {
        Logger.vv(TAG, "onDestroy");
        super.onDestroy();

        sInstance = null;
        unregisterReceiver(mBroadcastReceiver);
        unregister();
        disconnectEngineService();
        mRemoteClient.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!mRemoteClient.isConnected()) {
            mRemoteClient.connect();
        }

        return START_NOT_STICKY;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // messenger methods
    private void register() {
        sendMessage(obtainMessage(MSG_REGISTER_CLIENT));
    }

    private void unregister() {
        sendMessage(obtainMessage(MSG_UNREGISTER_CLIENT));
    }

    private Messenger getMessenger() {
        return mRemoteMessenger;
    }

    public Message obtainMessage(int messageId) {
        Message msg = mClientMessengerHandler.obtainMessage(messageId);
        msg.replyTo = mClientMessenger;
        return msg;
    }

    public void sendMessage(Message msg) {
        if(mRemoteMessenger == null) {
            //TODO: put messages to queue if we're waiting for connect
            //TODO: skip messages if connect failed (service is not installed)
            Log.v(TAG, "sendMessage: remote service is not connected");
            return;
        }

        try {
            mRemoteMessenger.send(msg);
        }
        catch(RemoteException e) {
            Log.e(TAG, "sendMessage: failed: " + e.getMessage());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // engine settings callbacks
    public void addEngineSettingsCallback(EngineSettingsCallback cb) {
        synchronized (mEngineSettingsCallbacks) {
            if (!mEngineSettingsCallbacks.contains(cb)) {
                mEngineSettingsCallbacks.add(cb);
            }
        }
    }

    public void removeEngineSettingsCallback(EngineSettingsCallback cb) {
        synchronized (mEngineSettingsCallbacks) {
            mEngineSettingsCallbacks.remove(cb);
        }
    }

    private void notifyEngineSettingsUpdated(@Nullable AceStreamPreferences preferences) {
        Logger.vv(TAG, "notifyEngineSettingsUpdated");

        if(preferences == null) return;

        mAceStreamPreferences = preferences;

        Logger.enableDebugLogging(mAceStreamPreferences.getBoolean("enable_debug_logging", false));

        synchronized(mEngineSettingsCallbacks) {
            for (EngineSettingsCallback callback : mEngineSettingsCallbacks) {
                callback.onEngineSettingsUpdated(preferences);
            }
        }

        for(org.acestream.engine.controller.Callback<AceStreamPreferences> callback: mOnEngineSettingsQueue) {
            callback.onSuccess(preferences);
        }
        mOnEngineSettingsQueue.clear();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // auth callbacks
    public void addAuthCallback(AuthCallback cb) {
        synchronized (mAuthCallbacks) {
            if (!mAuthCallbacks.contains(cb)) {
                mAuthCallbacks.add(cb);
            }
        }
    }

    public void removeAuthCallback(AuthCallback cb) {
        synchronized (mAuthCallbacks) {
            mAuthCallbacks.remove(cb);
        }
    }

    private void notifyAuthUpdated(AuthData authData) {
        Logger.vv(TAG, "notifyAuthUpdated: authData=" + authData);
        synchronized(mAuthCallbacks) {
            for (AuthCallback callback : mAuthCallbacks) {
                callback.onAuthUpdated(authData);
            }
        }
    }

    private void setAuthData(AuthData authData) {
        mCurrentAuthData = authData;
        notifyAuthUpdated(authData);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // engine status listeners
    public void addEngineStatusListener(EngineStatusListener listener) {
        mEngineStatusListeners.add(listener);
        updateEngineStatusListeners();
    }

    public void removeEngineStatusListener(EngineStatusListener listener) {
        mEngineStatusListeners.remove(listener);
        updateEngineStatusListeners();
    }

    private void notifyEngineStatus(EngineStatus status, RemoteDevice remoteDevice) {
        for(EngineStatusListener listener: mEngineStatusListeners) {
            listener.onEngineStatus(status, remoteDevice);
        }
    }

    private void updateEngineStatusListeners() {
        int count = mEngineStatusListeners.size();
        Log.d(TAG, "updateEngineStatusListeners: count=" + count);
        Message msg = obtainMessage(MSG_SET_ENGINE_STATUS_LISTENERS);
        Bundle data = new Bundle(1);
        data.putInt(MSG_PARAM_COUNT, count);
        msg.setData(data);
        sendMessage(msg);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // engine callbacks
    public void addEngineCallbackListener(EngineCallbackListener listener) {
        mEngineCallbackListeners.add(listener);
    }

    public void removeEngineCallbackListener(EngineCallbackListener listener) {
        mEngineCallbackListeners.remove(listener);
    }

    private void notifyRestartPlayer() {
        Logger.vv(TAG, "notifyRestartPlayer");
        for(EngineCallbackListener listener: mEngineCallbackListeners) {
            listener.onRestartPlayer();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // manager callbacks
    public void addCallback(Callback cb) {
        mCallbacks.add(cb);
    }

    public void removeCallback(Callback cb) {
        mCallbacks.remove(cb);
    }

    public void notifyBonusAdsAvailable(boolean available) {
        mBonusAdsAvailable = available;
        for (Callback callback : mCallbacks) {
            callback.onBonusAdsAvailable(available);
        }
    }

    public void notifyEngineConnected(EngineApi engineApi) {
        Logger.vv(TAG, "notifyEngineConnected");

        // notify listeners
        for (Callback callback : mCallbacks) {
            callback.onEngineConnected(engineApi);
        }

        // notify client who wait for engine
        for (EngineStateCallback callback : mEngineStateCallbacks) {
            callback.onEngineConnected(this, mEngineApi);
        }
        mEngineStateCallbacks.clear();
    }

    public void notifyEngineFailed() {
        Logger.vv(TAG, "notifyEngineFailed");
        for (Callback callback : mCallbacks) callback.onEngineFailed();
    }

    public void notifyEngineDisconnected() {
        Logger.vv(TAG, "notifyEngineDisconnected");
        for (Callback callback : mCallbacks) callback.onEngineFailed();
    }

    public void notifyEngineUnpacking() {
        Logger.vv(TAG, "notifyEngineUnpacking");
        for (Callback callback : mCallbacks) callback.onEngineUnpacking();
    }

    public void notifyEngineStarting() {
        Logger.vv(TAG, "notifyEngineStarting");
        for (Callback callback : mCallbacks) callback.onEngineStarting();
    }

    public void notifyEngineStopped() {
        Logger.vv(TAG, "notifyEngineStopped");
        for (Callback callback : mCallbacks) callback.onEngineStopped();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // discovery listeners
    public void addDeviceDiscoveryListener(DeviceDiscoveryListener listener) {
        mDeviceDiscoveryListeners.add(listener);
    }

    public void removeDeviceDiscoveryListener(DeviceDiscoveryListener listener) {
        mDeviceDiscoveryListeners.remove(listener);
    }

    private void notifyDeviceAdded(RemoteDevice device) {
        Logger.vv(TAG, "notifyDeviceAdded: device=" + device);
        for(DeviceDiscoveryListener listener: mDeviceDiscoveryListeners) {
            listener.onDeviceAdded(device);
        }
    }

    private void notifyCurrentDeviceChanged(RemoteDevice device) {
        Logger.vv(TAG, "notifyCurrentDeviceChanged: device=" + device);
        for(DeviceDiscoveryListener listener: mDeviceDiscoveryListeners) {
            listener.onCurrentDeviceChanged(device);
        }
    }

    private void notifyDeviceRemoved(@NonNull RemoteDevice device) {
        Logger.vv(TAG, "notifyDeviceRemoved: device=" + device);
        for (DeviceDiscoveryListener listener : mDeviceDiscoveryListeners) {
            listener.onDeviceRemoved(device);
        }

        removeRemoteDevice(device);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // remote device listeners
    public void addRemoteDeviceListener(RemoteDeviceListener listener) {
        mRemoteDeviceListeners.add(listener);
    }

    public void removeRemoteDeviceListener(RemoteDeviceListener listener) {
        mRemoteDeviceListeners.remove(listener);
    }

    private void notifyRemoteDeviceMessage(RemoteDevice device, JsonRpcMessage message) {
        // pass message to device to update its status
        device.onMessage(message);

        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onMessage(device, message);
        }
    }

    private void notifyRemoteDeviceConnected(RemoteDevice device) {
        Logger.vv(TAG, "notifyRemoteDeviceConnected: device=" + device);
        device.onConnected();
        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onConnected(device);
        }
    }

    private void notifyRemoteDeviceDisconnected(RemoteDevice device, boolean cleanShutdown) {
        device.onDisconnected();
        Logger.vv(TAG, "notifyRemoteDeviceDisconnected: device=" + device + " cleanShutdown=" + cleanShutdown);
        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onDisconnected(device, cleanShutdown);
        }
    }

    public void notifyAvailable(RemoteDevice device) {
        Logger.vv(TAG, "notifyAvailable: device=" + device);
        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onAvailable(device);
        }
    }

    public void notifyUnavailable(RemoteDevice device) {
        Logger.vv(TAG, "notifyUnavailable: device=" + device);
        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onUnavailable(device);
        }
    }

    public void notifyPingFailed(RemoteDevice device) {
        Logger.vv(TAG, "notifyPingFailed: device=" + device);
        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onPingFailed(device);
        }
    }

    public void notifyOutputFormatChanged(RemoteDevice device, String outputFormat) {
        Logger.vv(TAG, "notifyOutputFormatChanged: device=" + device + " outputFormat=" + outputFormat);
        for(RemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onOutputFormatChanged(device, outputFormat);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // playback status listeners (CSDK style)
    public void addPlaybackStatusListener(ConnectableDeviceListener listener) {
        mPlaybackStatusListeners.add(listener);
    }

    public void removePlaybackStatusListener(ConnectableDeviceListener listener) {
        mPlaybackStatusListeners.remove(listener);
    }

    private void notifyPlaybackStatus(RemoteDevice device, int status) {
        Logger.vv(TAG, "notifyPlaybackStatus: status=" + status);
        device.getCsdkListener().onStatus(device, status);
        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onStatus(device, status);
        }
    }

    private void notifyPlaybackPosition(RemoteDevice device, Long position) {
        Logger.vv(TAG, "notifyPlaybackPosition: position=" + position);
        device.getCsdkListener().onPosition(device, position);
        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onPosition(device, position);
        }
    }

    private void notifyPlaybackDuration(RemoteDevice device, Long duration) {
        Logger.vv(TAG, "notifyPlaybackDuration: duration=" + duration);
        device.getCsdkListener().onDuration(device, duration);
        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onDuration(device, duration);
        }
    }

    private void notifyPlaybackVolume(RemoteDevice device, float volume) {
        Logger.vv(TAG, "notifyPlaybackVolume: volume=" + volume);
        device.getCsdkListener().onVolume(device, volume);
        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onVolume(device, volume);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // playback state callbacks
    public void addPlaybackStateCallback(PlaybackStateCallback cb) {
        mPlaybackStateCallbacks.add(cb);
    }

    public void removePlaybackStateCallback(PlaybackStateCallback cb) {
        mPlaybackStateCallbacks.remove(cb);
    }

    private void notifyPlaybackStateStart(@Nullable EngineSession session) {
        Logger.vv(TAG, "notifyPlaybackStateStart: session=" + session);
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onStart(session);
        }
    }

    private void notifyPlaybackStatePrebuffering(@Nullable EngineSession session, int progress) {
        Logger.vv(TAG, "notifyPlaybackStatePrebuffering: progress=" + progress + " session=" + session);
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onPrebuffering(session, progress);
        }
    }

    private void notifyPlaybackStatePlay(@Nullable EngineSession session) {
        Logger.vv(TAG, "notifyPlaybackStatePlay: session=" + session);
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onPlay(session);
        }
    }

    private void notifyPlaybackStateStop() {
        Logger.vv(TAG, "notifyPlaybackStateStop");
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onStop();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // cast result listener
    public void registerCastResultListener(CastResultListener listener) {
        setCastResultListener(listener);
    }

    public void unregisterCastResultListener(CastResultListener listener) {
        if(listener == mCastResultListener) {
            setCastResultListener(null);
        }
    }

    private CastResultListener obtainCastResultListener(int hash) {
        return mCastResultListeners.get(hash);
    }

    private int hashCastResultListener(@NonNull CastResultListener listener) {
        int hash = listener.hashCode();
        mCastResultListeners.put(hash, listener);
        return hash;
    }

    private void releaseCastResultListener(int hash) {
        mCastResultListeners.remove(hash);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // engine session start listeners
    private EngineSessionStartListener obtainEngineSessionStartListener(int hash) {
        return mEngineSessionStartListeners.get(hash);
    }

    private int hashEngineSessionStartListener(@NonNull EngineSessionStartListener listener) {
        int hash = listener.hashCode();
        mEngineSessionStartListeners.put(hash, listener);
        return hash;
    }

    private void releaseEngineSessionStartListener(int hash) {
        mEngineSessionStartListeners.remove(hash);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // engine client callbacks
    @Override
    public void onConnected(IAceStreamEngine service) {
        Log.d(TAG, "onConnected: wasConnected=" + (mEngineApi != null));

        if(mEngineApi == null) {
            mEngineApi = new EngineApi(service);
        }

        notifyEngineConnected(mEngineApi);
    }

    @Override
    public void onFailed() {
        Log.d(TAG, "onFailed");
        notifyEngineFailed();
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
        notifyEngineDisconnected();
    }

    @Override
    public void onUnpacking() {
        Log.d(TAG, "onUnpacking");
        notifyEngineUnpacking();
    }

    @Override
    public void onStarting() {
        Log.d(TAG, "onStarting");
        notifyEngineStarting();
    }

    @Override
    public void onStopped() {
        Log.d(TAG, "onStopped");
        notifyEngineStopped();
        disconnectEngineService();
    }

    @Override
    public void onPlaylistUpdated() {
    }

    @Override
    public void onEPGUpdated() {
    }

    @Override
    public void onSettingsUpdated() {
    }

    @Override
    public void onRestartPlayer() {
        notifyRestartPlayer();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // service client methods
    private void initServiceClient() throws ServiceClient.ServiceMissingException {
        if(mEngineServiceClient == null) {
            mEngineServiceClient = new ServiceClient("AceStreamManager", this, this, false);
            mEngineServiceClient.bind();
        }
    }

    public void startEngine() {
        Log.d(TAG, "startEngine");
        try {
            initServiceClient();
            mEngineServiceClient.startEngine();
        }
        catch(ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "startEngine: service not installed");
        }
    }

    public void stopEngine() {
        Log.d(TAG, "stopEngine");
        sendMessage(obtainMessage(MSG_STOP_ENGINE));
    }

    public void clearCache() {
        Log.d(TAG, "clearCache");
        sendMessage(obtainMessage(MSG_CLEAR_CACHE));
    }

    public void getPreferences(@NonNull org.acestream.engine.controller.Callback<AceStreamPreferences> callback) {
        Log.d(TAG, "getPreferences");
        mOnEngineSettingsQueue.add(callback);
        sendMessage(obtainMessage(MSG_GET_PREFERENCES));
    }

    public void setPreferences(@NonNull Bundle preferences) {
        Log.d(TAG, "setPreferences");
        Message msg = obtainMessage(MSG_SET_PREFERENCES);
        Bundle data = new Bundle(1);
        data.putBundle(MSG_PARAM_PREFERENCES, preferences);
        msg.setData(data);
        sendMessage(msg);
    }

    // helpers
    public void setPreference(@NonNull String key, @Nullable Object value) {
        Bundle prefs = new Bundle(1);
        if(value == null) {
            prefs.putString(key, null);
            mAceStreamPreferences.putString(key, null);
        }
        else if(value instanceof String) {
            prefs.putString(key, (String) value);
            mAceStreamPreferences.putString(key, (String) value);
        }
        else if(value instanceof Boolean) {
            prefs.putBoolean(key, (boolean) value);
            mAceStreamPreferences.putBoolean(key, (boolean) value);
        }
        else {
            throw new IllegalStateException("String or boolean expected: value=" + value);
        }

        if(TextUtils.equals(key, "enable_debug_logging")) {
            Logger.enableDebugLogging((boolean)value);
        }

        setPreferences(prefs);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // private methods
    private void disconnectEngineService() {
        if(mEngineServiceClient != null) {
            mEngineServiceClient.unbind();
            mEngineServiceClient = null;
            mEngineApi = null;
        }
    }

    private void setEngineSession(@Nullable EngineSession session) {
        mEngineSession = session;
    }

    private void notifyServiceReady() {
        Logger.v(TAG, "notifyServiceReady: queue=" + mOnReadyQueue.size());
        mReady = true;
        for(Runnable runnable: mOnReadyQueue) {
            runnable.run();
        }
        mOnReadyQueue.clear();
    }

    private void runWhenReady(Runnable runnable) {
        if(mReady) {
            runnable.run();
        }
        else {
            mOnReadyQueue.add(runnable);
        }
    }

    private void runWhenReady(List<Runnable> list) {
        if(mReady) {
            for(Runnable runnable: list) {
                runnable.run();
            }
        }
        else {
            mOnReadyQueue.addAll(list);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // public methods
    public void shutdown() {
        Log.d(TAG, "shutdown");
        disconnectDevice();
        stopSelf();
    }

    public void getEngine(@NonNull EngineStateCallback callback) {
        getEngine(true, callback);
    }

    public void getEngine(boolean forceStart, @NonNull EngineStateCallback callback) {
        if(mEngineApi == null) {
            mEngineStateCallbacks.add(callback);
            if(forceStart) {
                startEngine();
            }
        }
        else {
            callback.onEngineConnected(this, mEngineApi);
        }
    }

    @Nullable
    public EngineSession getEngineSession() {
        return mEngineSession;
    }

    public void stopEngineSession(boolean sendStopCommand) {
        Logger.vv(TAG, "stopEngineSession: sendStopCommand=" + sendStopCommand);
        Message msg = obtainMessage(MSG_STOP_ENGINE_SESSION);
        Bundle data = new Bundle(1);
        data.putBoolean(MSG_PARAM_SEND_STOP_COMMAND, sendStopCommand);
        msg.setData(data);
        sendMessage(msg);
    }

    public void setOurPlayerActive(boolean value) {
        // noop
    }

    public void setPlayerActivityTimeout(int timeout) {
        Logger.vv(TAG, "setPlayerActivityTimeout: timeout=" + timeout);
        Message msg = obtainMessage(MSG_SET_PLAYER_ACTIVITY_TIMEOUT);
        Bundle data = new Bundle(1);
        data.putInt(MSG_PARAM_TIMEOUT, timeout);
        msg.setData(data);
        sendMessage(msg);
    }

    public void setHlsStream(int streamIndex) {
        Logger.vv(TAG, "setHlsStream: streamIndex=" + streamIndex);
        Message msg = obtainMessage(MSG_SET_HLS_STREAM);
        Bundle data = new Bundle(1);
        data.putInt(MSG_PARAM_STREAM_INDEX, streamIndex);
        msg.setData(data);
        sendMessage(msg);
    }

    public void stopRemotePlayback(boolean disconnectDevice) {
        Logger.vv(TAG, "stopRemotePlayback: disconnectDevice=" + disconnectDevice);
        Message msg = obtainMessage(MSG_STOP_REMOTE_PLAYBACK);
        Bundle data = new Bundle(1);
        data.putBoolean(MSG_PARAM_DISCONNECT_DEVICE, disconnectDevice);
        msg.setData(data);
        sendMessage(msg);
    }

    public void liveSeek(int position) {
        Logger.vv(TAG, "liveSeek: position=" + position);
        Message msg = obtainMessage(MSG_LIVE_SEEK);
        Bundle data = new Bundle(1);
        data.putInt(MSG_PARAM_POSITION, position);
        msg.setData(data);
        sendMessage(msg);
    }

    public void getMediaFileAsync(
            @NonNull final TransportFileDescriptor descriptor,
            @NonNull final MediaItem media,
            @NonNull final org.acestream.engine.controller.Callback<Pair<String, MediaFilesResponse.MediaFile>> callback
    ) {
        if(media.getUri() == null) {
            throw new IllegalStateException("missing uri");
        }

        if(mEngineApi == null) {
            Log.e(TAG, "getMediaFileAsync: missing engine api");
            callback.onError("Engine is not connected");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(MiscUtils.getQueryParameter(media.getUri(), "index"));
        }
        catch(NumberFormatException| UnsupportedEncodingException e) {
            index = 0;
        }
        final int fileIndex = index;

        mEngineApi.getMediaFiles(descriptor, new org.acestream.engine.controller.Callback<MediaFilesResponse>() {
            @Override
            public void onSuccess(MediaFilesResponse result) {
                for(MediaFilesResponse.MediaFile mf: result.files) {
                    if(mf.index == fileIndex) {
                        media.setLive(mf.isLive());
                        media.setP2PInfo(mf.infohash, mf.index);
                        callback.onSuccess(new Pair<>(result.transport_file_data, mf));
                        return;
                    }
                }
                Log.e(TAG, "Bad file index: index=" + fileIndex);
            }

            @Override
            public void onError(String err) {
                callback.onError(err);
            }
        });
    }

    public Collection<RemoteDevice> getRemoteDevices() {
        return mRemoteDevices.values();
    }

    public void startCastDevice(String deviceId, boolean restartFromLastPosition, long startFrom,
                                @Nullable CastResultListener listener) {
        Logger.vv(TAG, "startCastDevice: deviceId=" + deviceId + " restart=" + restartFromLastPosition + " startFrom=" + startFrom);
        Message msg = obtainMessage(MSG_START_CAST_DEVICE);
        Bundle data = new Bundle(4);
        data.putString(MSG_PARAM_REMOTE_DEVICE_ID, deviceId);
        data.putBoolean(MSG_PARAM_RESTART_FROM_LAST_POSITION, restartFromLastPosition);
        data.putLong(MSG_PARAM_START_FROM, startFrom);
        if(listener == null) {
            listener = mCastResultListener;
        }
        if(listener != null) {
            data.putInt(MSG_PARAM_CAST_RESULT_LISTENER, hashCastResultListener(listener));
        }
        msg.setData(data);
        sendMessage(msg);
    }

    public void disconnectDevice() {
        Logger.vv(TAG, "disconnectDevice");
        sendMessage(obtainMessage(MSG_DISCONNECT_DEVICE));
    }

    public void discoverDevices(boolean forceInit) {
        Logger.vv(TAG, "discoverDevices: forceInit=" + forceInit);
        Message msg = obtainMessage(MSG_DISCOVER_DEVICES);
        Bundle data = new Bundle(1);
        data.putBoolean(MSG_PARAM_FORCE_INIT, forceInit);
        msg.setData(data);
        sendMessage(msg);
    }

    @Nullable
    public RemoteDevice findRemoteDevice(SelectedPlayer player) {
        for(RemoteDevice device: mRemoteDevices.values()) {
            if(device.equals(player)) {
                return device;
            }
        }
        return null;
    }

    @Nullable
    public RemoteDevice findRemoteDeviceByIp(String ip, int type) {
        for(RemoteDevice device: mRemoteDevices.values()) {
            if(device.getDeviceType() == type && TextUtils.equals(ip, device.getIpAddress())) {
                return device;
            }
        }
        return null;
    }

    @Nullable
    public AuthData getAuthData() {
        return mCurrentAuthData;
    }

    public int getAuthLevel() {
        return mCurrentAuthData == null ? 0 : mCurrentAuthData.auth_level;
    }

    @Nullable
    public String getAuthLogin() {
        return mCurrentAuthData == null ? null : mCurrentAuthData.login;
    }

    public void signOut() {
        sendMessage(obtainMessage(MSG_SIGN_OUT));
    }

    public void checkPendingNotification() {
        sendMessage(obtainMessage(MSG_CHECK_PENDING_NOTIFICATIONS));
    }

    public void initEngineSession(PlaybackData playbackData, @Nullable EngineSessionStartListener listener) {
        Logger.vv(TAG, "initEngineSession: playbackData=" + playbackData);
        Message msg = obtainMessage(MSG_INIT_ENGINE_SESSION);
        Bundle data = new Bundle(2);
        data.putString(MSG_PARAM_PLAYBACK_DATA, playbackData.toJson());
        if(listener != null) {
            data.putInt(MSG_PARAM_ENGINE_SESSION_START_LISTENER, hashEngineSessionStartListener(listener));
        }
        msg.setData(data);
        sendMessage(msg);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // remote devices
    private RemoteDevice obtainRemoteDevice(String json) {
        RemoteDevice device = RemoteDevice.fromJson(this, json);
        RemoteDevice cached = mRemoteDevices.get(device.getId());
        if(cached == null) {
            mRemoteDevices.put(device.getId(), device);
            return device;
        }

        return cached;
    }

    private void removeRemoteDevice(@NonNull RemoteDevice device) {
        Logger.vv(TAG, "removeRemoteDevice: device=" + device);
        mRemoteDevices.remove(device.getId());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Client

    // Constants: actions and messages
    // Action used to bind to remote service
    public static final String REMOTE_BIND_ACTION = "org.acestream.engine.PlaybackManager";

    // Remote messages: service -> client
    public final static int MSG_AUTH_UPDATED = 0;
    public final static int MSG_ENGINE_SETTINGS_UPDATED = 1;
    public final static int MSG_ENGINE_STATUS = 2;
    public final static int MSG_DEVICE_ADDED = 3;
    public final static int MSG_DEVICE_REMOVED = 4;
    public final static int MSG_DEVICE_CHANGED = 5;

    public final static int MSG_DEVICE_ON_MESSAGE = 6;
    public final static int MSG_DEVICE_ON_CONNECTED = 7;
    public final static int MSG_DEVICE_ON_DISCONNECTED = 8;
    public final static int MSG_DEVICE_ON_AVAILABLE = 9;
    public final static int MSG_DEVICE_ON_UNAVAILABLE = 10;
    public final static int MSG_DEVICE_ON_PING_FAILED = 11;
    public final static int MSG_DEVICE_ON_OUTPUT_FORMAT_CHANGED = 12;

    public final static int MSG_DEVICE_ON_PLAYBACK_STATUS = 13;
    public final static int MSG_DEVICE_ON_PLAYBACK_POSITION = 14;
    public final static int MSG_DEVICE_ON_PLAYBACK_DURATION = 15;
    public final static int MSG_DEVICE_ON_PLAYBACK_VOLUME = 16;

    public final static int MSG_PLAYBACK_STATE_START = 17;
    public final static int MSG_PLAYBACK_STATE_PREBUFFERING = 18;
    public final static int MSG_PLAYBACK_STATE_PLAY = 19;
    public final static int MSG_PLAYBACK_STATE_STOP = 20;

    public final static int MSG_CAST_RESULT_LISTENER_SUCCESS = 21;
    public final static int MSG_CAST_RESULT_LISTENER_ERROR = 22;
    public final static int MSG_CAST_RESULT_LISTENER_DEVICE_CONNECTED = 23;
    public final static int MSG_CAST_RESULT_LISTENER_DEVICE_DISCONNECTED = 24;
    public final static int MSG_CAST_RESULT_LISTENER_CANCEL = 25;

    public final static int MSG_ENGINE_SESSION_START_LISTENER_SUCCESS = 26;
    public final static int MSG_ENGINE_SESSION_START_LISTENER_ERROR = 27;

    public final static int MSG_ENGINE_SESSION_STARTED = 28;
    public final static int MSG_ENGINE_SESSION_STOPPED = 29;

    // Remote service is fully usable after receiving this message
    public final static int MSG_SERVICE_READY = 30;
    public final static int MSG_BONUS_ADS_AVAILABLE = 31;

    // Remote messages: client -> service
    public final static int MSG_REGISTER_CLIENT = 1000;
    public final static int MSG_UNREGISTER_CLIENT = 1001;
    public final static int MSG_DEVICE_PLAY = 1002;
    public final static int MSG_DEVICE_PAUSE = 1003;
    public final static int MSG_DEVICE_STOP = 1004;
    public final static int MSG_DEVICE_SET_TIME = 1005;
    public final static int MSG_DEVICE_SET_VOLUME = 1006;
    public final static int MSG_DEVICE_SET_POSITION = 1007;
    public final static int MSG_DEVICE_SET_AUDIO_TRACK = 1008;
    public final static int MSG_DEVICE_SET_SPU_TRACK = 1009;
    public final static int MSG_DEVICE_SET_VIDEO_SIZE = 1010;
    public final static int MSG_DEVICE_SET_AUDIO_OUTPUT = 1011;
    public final static int MSG_DEVICE_SET_AUDIO_DIGITAL_OUTPUT_ENABLED = 1012;
    public final static int MSG_SIGN_OUT = 1013;
    public final static int MSG_DISCOVER_DEVICES = 1014;
    public final static int MSG_DISCONNECT_DEVICE = 1015;
    public final static int MSG_START_ACECAST = 1016;
    public final static int MSG_START_CAST_DEVICE = 1017;
    public final static int MSG_STOP_ENGINE_SESSION = 1018;
    public final static int MSG_SET_PLAYER_ACTIVITY_TIMEOUT = 1019;
    public final static int MSG_SET_HLS_STREAM = 1020;
    public final static int MSG_STOP_REMOTE_PLAYBACK = 1021;
    public final static int MSG_LIVE_SEEK = 1022;
    public final static int MSG_INIT_ENGINE_SESSION = 1023;
    public final static int MSG_STOP_ENGINE = 1024;
    public final static int MSG_CLEAR_CACHE = 1025;
    public final static int MSG_SET_PREFERENCES = 1026;
    public final static int MSG_GET_PREFERENCES = 1027;
    public final static int MSG_FORGET_SELECTED_PLAYER = 1028;
    public final static int MSG_SAVE_SELECTED_PLAYER = 1029;
    public final static int MSG_CHECK_PENDING_NOTIFICATIONS = 1030;
    public final static int MSG_SHOW_BONUS_ADS = 1031;
    public final static int MSG_SET_ENGINE_STATUS_LISTENERS = 1032;

    // Remote messages params
    public final static String MSG_PARAM_AUTH_DATA = "auth_data";
    public final static String MSG_PARAM_ENGINE_STATUS = "engine_status";
    public final static String MSG_PARAM_REMOTE_DEVICE = "remove_device";
    public final static String MSG_PARAM_REMOTE_DEVICE_ID = "remove_device_id";
    public final static String MSG_PARAM_IS_ACECAST = "is_acecast";
    public final static String MSG_PARAM_JSON_RPC_MESSAGE = "json_rpc_message";
    public final static String MSG_PARAM_CLEAN_SHUTDOWN = "clean_shutdown";
    public final static String MSG_PARAM_OUTPUT_FORMAT = "output_format";
    public final static String MSG_PARAM_PLAYBACK_STATUS = "playback_status";
    public final static String MSG_PARAM_POSITION = "position";
    public final static String MSG_PARAM_DURATION = "duration";
    public final static String MSG_PARAM_VOLUME = "volume";
    public final static String MSG_PARAM_ENGINE_SESSION = "engine_session";
    public final static String MSG_PARAM_PROGRESS = "progress";
    public final static String MSG_PARAM_DISCONNECT = "disconnect";
    public final static String MSG_PARAM_TIME = "time";
    public final static String MSG_PARAM_TRACK = "track";
    public final static String MSG_PARAM_VIDEO_SIZE = "video_size";
    public final static String MSG_PARAM_ENABLED = "enabled";
    public final static String MSG_PARAM_AOUT = "aout";
    public final static String MSG_PARAM_FORCE_INIT = "force_init";
    public final static String MSG_PARAM_PLAYBACK_DATA = "playback_data";
    public final static String MSG_PARAM_SAVED_TIME = "saved_time";
    public final static String MSG_PARAM_CAST_RESULT_LISTENER = "cast_result_listener";
    public final static String MSG_PARAM_SELECTED_PLAYER = "selected_player";
    public final static String MSG_PARAM_ERROR = "error";
    public final static String MSG_PARAM_RESTART_FROM_LAST_POSITION = "restart_from_last_position";
    public final static String MSG_PARAM_START_FROM = "start_from";
    public final static String MSG_PARAM_SEND_STOP_COMMAND = "send_stop_command";
    public final static String MSG_PARAM_TIMEOUT = "timeout";
    public final static String MSG_PARAM_STREAM_INDEX = "stream_index";
    public final static String MSG_PARAM_DISCONNECT_DEVICE = "disconnect_device";
    public final static String MSG_PARAM_ENGINE_SESSION_START_LISTENER = "engine_session_start_listener";
    public final static String MSG_PARAM_PREFERENCES = "preferences";
    public final static String MSG_PARAM_FROM_USER = "from_user";
    public final static String MSG_PARAM_AVAILABLE = "available";
    public final static String MSG_PARAM_COUNT = "count";

    // Handle messages from remote PlaybackManager service
    @SuppressLint("HandlerLeak")
    class ClientMessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                Bundle d = msg.getData();
                switch (msg.what) {
                    case MSG_SERVICE_READY:
                        notifyServiceReady();
                        break;
                    case MSG_AUTH_UPDATED:
                        setAuthData(AuthData.fromJson(d.getString(MSG_PARAM_AUTH_DATA)));
                        break;
                    case MSG_BONUS_ADS_AVAILABLE:
                        notifyBonusAdsAvailable(d.getBoolean(MSG_PARAM_AVAILABLE));
                        break;
                    case MSG_ENGINE_SETTINGS_UPDATED:
                        notifyEngineSettingsUpdated(
                                AceStreamPreferences.fromBundle(d.getBundle(MSG_PARAM_PREFERENCES)));
                        break;
                    case MSG_ENGINE_STATUS: {
                        RemoteDevice device = null;
                        if(d.containsKey(MSG_PARAM_REMOTE_DEVICE)) {
                            device = obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE));
                        }
                        notifyEngineStatus(
                                EngineStatus.fromJson(d.getString(MSG_PARAM_ENGINE_STATUS)),
                                device);
                        break;
                    }
                    case MSG_DEVICE_ADDED:
                        notifyDeviceAdded(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_REMOVED:
                        notifyDeviceRemoved(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_CHANGED:
                        notifyCurrentDeviceChanged(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_ON_MESSAGE:
                        notifyRemoteDeviceMessage(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                JsonRpcMessage.fromString(d.getString(MSG_PARAM_JSON_RPC_MESSAGE)));
                        break;
                    case MSG_DEVICE_ON_CONNECTED:
                        notifyRemoteDeviceConnected(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_ON_DISCONNECTED:
                        notifyRemoteDeviceDisconnected(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                d.getBoolean(MSG_PARAM_CLEAN_SHUTDOWN));
                        break;
                    case MSG_DEVICE_ON_AVAILABLE:
                        notifyAvailable(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_ON_UNAVAILABLE:
                        notifyUnavailable(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_ON_PING_FAILED:
                        notifyPingFailed(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    case MSG_DEVICE_ON_OUTPUT_FORMAT_CHANGED:
                        notifyOutputFormatChanged(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                d.getString(MSG_PARAM_OUTPUT_FORMAT));
                        break;
                    case MSG_DEVICE_ON_PLAYBACK_STATUS:
                        notifyPlaybackStatus(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                d.getInt(MSG_PARAM_PLAYBACK_STATUS));
                        break;
                    case MSG_DEVICE_ON_PLAYBACK_POSITION:
                        notifyPlaybackPosition(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                d.getLong(MSG_PARAM_POSITION));
                        break;
                    case MSG_DEVICE_ON_PLAYBACK_DURATION:
                        notifyPlaybackDuration(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                d.getLong(MSG_PARAM_DURATION));
                        break;
                    case MSG_DEVICE_ON_PLAYBACK_VOLUME:
                        notifyPlaybackVolume(
                                obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                d.getFloat(MSG_PARAM_VOLUME));
                        break;
                    case MSG_PLAYBACK_STATE_START:
                        notifyPlaybackStateStart(EngineSession.fromJson(d.getString(MSG_PARAM_ENGINE_SESSION)));
                        break;
                    case MSG_PLAYBACK_STATE_PREBUFFERING:
                        notifyPlaybackStatePrebuffering(
                                EngineSession.fromJson(d.getString(MSG_PARAM_ENGINE_SESSION)),
                                d.getInt(MSG_PARAM_PROGRESS));
                        break;
                    case MSG_PLAYBACK_STATE_PLAY:
                        notifyPlaybackStatePlay(EngineSession.fromJson(d.getString(MSG_PARAM_ENGINE_SESSION)));
                        break;
                    case MSG_PLAYBACK_STATE_STOP:
                        notifyPlaybackStateStop();
                        break;
                    case MSG_CAST_RESULT_LISTENER_SUCCESS: {
                        CastResultListener listener = obtainCastResultListener(d.getInt(MSG_PARAM_CAST_RESULT_LISTENER));
                        if (listener != null) {
                            if (d.containsKey(MSG_PARAM_REMOTE_DEVICE)) {
                                listener.onSuccess(
                                        obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)),
                                        SelectedPlayer.fromJson(d.getString(MSG_PARAM_SELECTED_PLAYER)));
                            } else {
                                listener.onSuccess();
                            }
                        }
                        break;
                    }
                    case MSG_CAST_RESULT_LISTENER_ERROR: {
                        CastResultListener listener = obtainCastResultListener(d.getInt(MSG_PARAM_CAST_RESULT_LISTENER));
                        if (listener != null)
                            listener.onError(d.getString(MSG_PARAM_ERROR));
                        break;
                    }
                    case MSG_CAST_RESULT_LISTENER_DEVICE_CONNECTED: {
                        CastResultListener listener = obtainCastResultListener(d.getInt(MSG_PARAM_CAST_RESULT_LISTENER));
                        if (listener != null)
                            listener.onDeviceConnected(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    }
                    case MSG_CAST_RESULT_LISTENER_DEVICE_DISCONNECTED: {
                        CastResultListener listener = obtainCastResultListener(d.getInt(MSG_PARAM_CAST_RESULT_LISTENER));
                        if (listener != null)
                            listener.onDeviceDisconnected(obtainRemoteDevice(d.getString(MSG_PARAM_REMOTE_DEVICE)));
                        break;
                    }
                    case MSG_CAST_RESULT_LISTENER_CANCEL: {
                        CastResultListener listener = obtainCastResultListener(d.getInt(MSG_PARAM_CAST_RESULT_LISTENER));
                        if (listener != null)
                            listener.onCancel();
                        break;
                    }
                    case MSG_ENGINE_SESSION_START_LISTENER_SUCCESS: {
                        int hash = d.getInt(MSG_PARAM_ENGINE_SESSION_START_LISTENER);
                        EngineSessionStartListener listener = obtainEngineSessionStartListener(hash);
                        if (listener != null) {
                            listener.onSuccess(EngineSession.fromJson(d.getString(MSG_PARAM_ENGINE_SESSION)));
                            // release because listener is used only once
                            releaseEngineSessionStartListener(hash);
                        }
                        break;
                    }
                    case MSG_ENGINE_SESSION_START_LISTENER_ERROR: {
                        int hash = d.getInt(MSG_PARAM_ENGINE_SESSION_START_LISTENER);
                        EngineSessionStartListener listener = obtainEngineSessionStartListener(hash);
                        if (listener != null) {
                            listener.onError(d.getString(MSG_PARAM_ERROR));
                            // release because listener is used only once
                            releaseEngineSessionStartListener(hash);
                        }
                        break;
                    }
                    case MSG_ENGINE_SESSION_STARTED:
                        if(!d.containsKey(MSG_PARAM_ENGINE_SESSION)) {
                            Log.e(TAG, "handleMessage:MSG_ENGINE_SESSION_STARTED: missing session");
                            break;
                        }
                        setEngineSession(EngineSession.fromJson(d.getString(MSG_PARAM_ENGINE_SESSION)));
                        break;
                    case MSG_ENGINE_SESSION_STOPPED:
                        setEngineSession(null);
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
            catch(Throwable e) {
                Log.e(TAG, "handleMessage: error", e);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // local client
    public static class Client {
        public static final String TAG = "AS/Manager/Client";

        public interface Callback {
            void onConnected(AceStreamManager service);
            void onDisconnected();
        }

        private boolean mBound = false;
        private boolean mConnected = false;
        private AceStreamManager mService = null;
        private final AceStreamManager.Client.Callback mCallback;
        private final Context mContext;
        private final List<Runnable> mOnConnectedQueue = new CopyOnWriteArrayList<>();
        private final List<Runnable> mOnReadyQueue = new CopyOnWriteArrayList<>();

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                Log.d(TAG, "onServiceConnected: bound=" + mBound + " context=" + mContext);

                if (!mBound)
                    return;

                mConnected = true;
                mService = AceStreamManager.getService(iBinder);
                mCallback.onConnected(mService);
                notifyConnected();
                if(mOnReadyQueue.size() > 0) {
                    mService.runWhenReady(mOnReadyQueue);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected: context=" + mContext);

                mBound = false;
                mConnected = false;
                mService = null;
                mCallback.onDisconnected();
            }
        };

        private static Intent getServiceIntent(Context context) {
            return new Intent(context, AceStreamManager.class);
        }

        private static void startService(Context context) {
            context.startService(getServiceIntent(context));
        }

        private static void stopService(Context context) {
            context.stopService(getServiceIntent(context));
        }

        public Client(Context context, AceStreamManager.Client.Callback callback) {
            if (context == null || callback == null) throw new IllegalArgumentException("Context and callback can't be null");
            mContext = context;
            mCallback = callback;
        }

        public void connect() {
            connect(true);
        }

        public void connect(boolean persist) {
            Log.d(TAG, "connect: bound=" + mBound + " persist=" + persist + " context=" + mContext);
            if (mBound) {
                if(BuildConfig.DEBUG) {
                    throw new IllegalStateException("already connected");
                }
                else {
                    Log.e(TAG, "connect: already connected: context=" + mContext);
                    return;
                }
            }
            final Intent serviceIntent = getServiceIntent(mContext);
            if(persist) {
                mContext.startService(serviceIntent);
            }
            mBound = mContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

        public boolean isConnected() {
            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }
            return mBound;
        }

        public void disconnect() {
            Log.d(TAG, "disconnect: bound=" + mBound + " context=" + mContext);

            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }

            mConnected = false;
            mService = null;
            if (mBound) {
                mBound = false;
                try {
                    mContext.unbindService(mServiceConnection);
                }
                catch(IllegalArgumentException e) {
                    if(BuildConfig.DEBUG) {
                        throw e;
                    }
                    else {
                        Logger.wtf(TAG, "disconnect: error", e);
                    }
                }
                mCallback.onDisconnected();
            }
        }

        private void notifyConnected() {
            for(Runnable runnable: mOnConnectedQueue) {
                runnable.run();
            }
            mOnConnectedQueue.clear();
        }

        public void runWhenConnected(Runnable runnable) {
            if(mConnected) {
                runnable.run();
            }
            else {
                mOnConnectedQueue.add(runnable);
            }
        }

        public void runWhenReady(Runnable runnable) {
            if(mConnected) {
                mService.runWhenReady(runnable);
            }
            else {
                mOnReadyQueue.add(runnable);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // remote client
    public static class RemoteClient {
        public static final String TAG = "AS/Manager/RClient";

        public interface Callback {
            void onConnected(Messenger remoteMessenger);
            void onDisconnected();
        }

        private boolean mBound = false;
        private boolean mConnected = false;
        private final Callback mCallback;
        private final Context mContext;

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.d(TAG, "onServiceConnected: bound=" + mBound + " connected=" + mConnected);

                if(mConnected) {
                    return;
                }

                mConnected = true;
                mCallback.onConnected(new Messenger(binder));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected");
                mBound = false;
                mConnected = false;

                mCallback.onDisconnected();
            }
        };

        public RemoteClient(Context context, RemoteClient.Callback callback) {
            if (context == null || callback == null) {
                throw new IllegalArgumentException("Context and callback can't be null");
            }
            mContext = context;
            mCallback = callback;
        }

        @MainThread
        public boolean connect() {
            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }

            if (mBound) {
                Log.v(TAG, "connect: already connected");
                return false;
            }

            Log.d(TAG, "connect: connected=" + mConnected);

            final Intent serviceIntent;
            try {
                serviceIntent = getServiceIntent(mContext);
                mContext.startService(serviceIntent);
                mBound = mContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
            }
            catch (ServiceClient.ServiceMissingException e) {
                Log.e(TAG, "Cannot connect: AceStream is not installed");
            }
            catch(Throwable e) {
                Log.e(TAG, "Unexpected error while starting service", e);
            }

            return true;
        }

        @MainThread
        public void disconnect() {
            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }

            Log.d(TAG, "disconnect: bound=" + mBound + " connected=" + mConnected);
            if (mBound) {
                mBound = false;
                mConnected = false;
                mContext.unbindService(mServiceConnection);
            }
        }

        public boolean isConnected() {
            return mConnected;
        }

        private static Intent getServiceIntent(Context context) throws ServiceClient.ServiceMissingException {
            Intent intent = new Intent(REMOTE_BIND_ACTION);
            intent.setPackage(ServiceClient.getServicePackage(context));
            return intent;
        }

        public static void startService(Context context) throws ServiceClient.ServiceMissingException {
            Log.v(TAG, "startService");
            context.startService(getServiceIntent(context));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void startPlayer(
            final Context context,
            final SelectedPlayer player,
            final MediaItem media,
            final int streamIndex,
            final CastResultListener listener,
            final int forceResume) {

        TransportFileDescriptor descriptor;
        try {
            descriptor = media.getDescriptor();
        }
        catch(TransportFileParsingException e) {
            Log.e(TAG, "Failed to read transport file", e);
            listener.onError(e.getMessage());
            return;
        }

        final long savedTime = media.getSavedTime();

        MediaFilesResponse.MediaFile mediaFile = media.getMediaFile();
        if(mediaFile == null) {
            final TransportFileDescriptor fDescriptor = descriptor;
            Log.v(TAG, "startPlayer: no media file, get from engine: descriptor=" + descriptor);
            getMediaFileAsync(descriptor, media, new org.acestream.engine.controller.Callback<Pair<String, MediaFilesResponse.MediaFile>>() {
                @Override
                public void onSuccess(Pair<String, MediaFilesResponse.MediaFile> result) {
                    fDescriptor.setTransportFileData(result.first);
                    startPlayer(context, player, fDescriptor, result.second, streamIndex, listener, forceResume, savedTime);
                }

                @Override
                public void onError(String err) {
                    listener.onError(err);
                }
            });
            return;
        }

        // Got descriptor and media file. Start now.
        startPlayer(context, player, descriptor, mediaFile, streamIndex, listener, forceResume, savedTime);
    }

    private void startPlayer(
            final Context context,
            final SelectedPlayer player,
            final TransportFileDescriptor descriptor,
            final MediaFilesResponse.MediaFile mediaFile,
            int streamIndex,
            final CastResultListener listener,
            int forceResume,
            long savedTime) {
        Logger.v(TAG, "startPlayer: player=" + player
                + " descriptor=" + descriptor
                + " mediaFile=" + mediaFile
                + " forceResume=" + forceResume
                + " savedTime=" + savedTime
        );
        final PlaybackData playbackData = new PlaybackData();
        playbackData.descriptor = descriptor;
        playbackData.mediaFile = mediaFile;
        playbackData.streamIndex = streamIndex;

        setCastResultListener(listener);

        if(player.type == SelectedPlayer.CONNECTABLE_DEVICE) {
            RunnableWithParams<Pair<Boolean, Long>> runnable = new RunnableWithParams<Pair<Boolean, Long>>() {
                @Override
                public void run(Pair<Boolean,Long> data) {
                    playbackData.outputFormat = getOutputFormatForContent(
                            mediaFile.type,
                            mediaFile.mime,
                            player.id1,
                            true,
                            false);
                    playbackData.useFixedSid = false;
                    playbackData.stopPrevReadThread = 0;
                    playbackData.resumePlayback = data.first;
                    playbackData.seekOnStart = data.second;
                    initEngineSession(playbackData, null);
                }
            };
            if(forceResume == 1) {
                runnable.run(new Pair<>(true, savedTime));
            }
            else if(forceResume == 0) {
                runnable.run(new Pair<>(false, 0L));
            }
            else {
                checkResumeOptions(context, mediaFile.infohash, mediaFile.index, savedTime, runnable);
            }
        }
        else if(player.type == SelectedPlayer.ACESTREAM_DEVICE) {
            RunnableWithParams<Pair<Boolean, Long>> runnable = new RunnableWithParams<Pair<Boolean, Long>>() {
                @Override
                public void run(Pair<Boolean, Long> data) {
                    playbackData.useTimeshift = true;
                    startAceCast(playbackData, player.id1, data.second, listener);
                }
            };
            if(forceResume == 1) {
                runnable.run(new Pair<>(true, savedTime));
            }
            else if(forceResume == 0) {
                runnable.run(new Pair<>(false, 0L));
            }
            else {
                checkResumeOptions(context, mediaFile.infohash, mediaFile.index, savedTime, runnable);
            }
        }
        else if(player.type == SelectedPlayer.LOCAL_PLAYER) {
            playbackData.outputFormat = getOutputFormatForContent(
                    mediaFile.type,
                    mediaFile.mime,
                    player.id1,
                    false,
                    false);
            playbackData.useFixedSid = false;
            playbackData.stopPrevReadThread = 0;
            initEngineSession(playbackData, null);
        }
        else {
            throw new IllegalStateException("unexpected player type: " + player.type);
        }
    }

    private void checkResumeOptions(Context context, String infohash, int fileIndex, final long savedTime, final RunnableWithParams<Pair<Boolean,Long>> runnable) {
        if(savedTime == 0) {
            // no saved position
            runnable.run(new Pair<>(false, 0L));
            return;
        }

        // got saved position, prompt user what to do
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setMessage(R.string.want_restart);
        builder.setPositiveButton(R.string.restart_from_beginning, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runnable.run(new Pair<>(false, 0L));
            }
        });
        builder.setNegativeButton(R.string.resume, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runnable.run(new Pair<>(true, savedTime));
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                runnable.run(new Pair<>(false, 0L));
            }
        });
        builder.create().show();
    }

    private void startAceCast(
            @NonNull PlaybackData playbackData,
            String deviceId,
            long savedTime,
            @NonNull final CastResultListener listener) {
        Logger.vv(TAG, "startAceCast: deviceId=" + deviceId + " playbackData=" + playbackData);
        Message msg = obtainMessage(MSG_START_ACECAST);
        Bundle data = new Bundle(4);
        data.putString(MSG_PARAM_PLAYBACK_DATA, playbackData.toJson());
        data.putString(MSG_PARAM_REMOTE_DEVICE_ID, deviceId);
        data.putLong(MSG_PARAM_SAVED_TIME, savedTime);
        data.putInt(MSG_PARAM_CAST_RESULT_LISTENER, hashCastResultListener(listener));
        msg.setData(data);
        sendMessage(msg);
    }

    private void setCastResultListener(@Nullable final CastResultListener listener) {
        if(mCastResultListener != listener) {
            if(mCastResultListener != null) {
                // cancel prev listener
                Log.d(TAG, "setCastResultListener: cancel prev listener: prev=" + mCastResultListener + " new=" + listener);
                mCastResultListener.onCancel();
                releaseCastResultListener(mCastResultListener.hashCode());
            }

            mCastResultListener = listener;
        }
    }

    @Override
    public OutputFormat getOutputFormatForContent(String type, String mime, String playerPackageName, boolean isAirCast, boolean isOurPlayer) {
        boolean transcodeAudio = false;
        boolean transcodeMP3 = false;
        boolean transcodeAC3 = false;

        String outputFormat;
        String prefsOutputFormat;
        if(type.equals(CONTENT_TYPE_VOD)) {
            prefsOutputFormat = getVodOutputFormat();
        }
        else {
            prefsOutputFormat = getLiveOutputFormat();
        }

        outputFormat = prefsOutputFormat;
        //noinspection IfCanBeSwitch
        if(prefsOutputFormat.equals("original")) {
            if(mime.equals(MIME_HLS)) {
                outputFormat = "hls";
            }
            else {
                outputFormat = "http";
            }
        }
        else if(prefsOutputFormat.equals("hls")) {
            transcodeAudio = getTranscodeAudio();
            transcodeAC3 = getTranscodeAC3();
        }
        else if(prefsOutputFormat.equals("auto")) {
            // auto selection based on content and player

            if(isOurPlayer) {
                outputFormat = "http";
            }
            else {
                boolean isVLC = false;
                boolean isMX = false;
                if (playerPackageName != null) {
                    switch (playerPackageName) {
                        case Constants.VLC_PACKAGE_NAME:
                        case Constants.VLC_BETA_PACKAGE_NAME:
                        case Constants.VLC_DEBUG_PACKAGE_NAME:
                            isVLC = true;
                            break;
                        case Constants.MX_FREE_PACKAGE_NAME:
                        case Constants.MX_PRO_PACKAGE_NAME:
                            isMX = true;
                            break;
                    }
                }

                if (type.equals(CONTENT_TYPE_VOD)) {
                    if (mime.startsWith("audio/")) {
                        // audio, http
                        outputFormat = "http";
                    } else {
                        // video
                        if (isVLC) {
                            // VLC, http
                            outputFormat = "http";
                        } else if (isMX) {
                            // MX Player
                            // mkv - HLS with AC3 transcoding
                            // other containers - http
                            if (mime.equals("video/x-matroska")) {
                                outputFormat = "hls";
                                transcodeAC3 = true;
                            } else {
                                outputFormat = "http";
                            }
                        } else if (isAirCast) {
                            // chromecast, airplay: HLS with AC3 transcoding
                            outputFormat = "hls";
                            transcodeAC3 = true;
                        } else {
                            // other players
                            // mkv - HLS with AC3 transcoding
                            // other containers - http
                            if (mime.equals("video/x-matroska")) {
                                outputFormat = "hls";
                                transcodeAC3 = true;
                            } else {
                                outputFormat = "http";
                            }
                        }
                    }
                } else {
                    // live, HLS
                    if (isVLC) {
                        outputFormat = "http";
                    } else if (isMX) {
                        // MX - HLS
                        outputFormat = "hls";
                    } else if (isAirCast) {
                        // aircast - always HLS
                        outputFormat = "hls";
                    } else {
                        // other players - HLS
                        outputFormat = "hls";
                    }
                    transcodeMP3 = false;
                    //noinspection RedundantIfStatement
                    if (isMX || isVLC) {
                        // MX and VLC - don't transcode
                        transcodeAudio = false;
                    } else {
                        // other players - transcode all audio codecs except AAC and MP3
                        transcodeAudio = true;
                    }
                }
            }
        }

        Log.d(TAG, String.format(
                "getOutputFormatForContent: prefs=%s format=%s ta=%s mp3=%s ac3=%s type=%s mime=%s player=%s isAirCast=%s",
                prefsOutputFormat,
                outputFormat,
                transcodeAudio,
                transcodeMP3,
                transcodeAC3,
                type,
                mime,
                playerPackageName,
                isAirCast));

        OutputFormat of = new OutputFormat();
        of.format = outputFormat;
        of.transcodeAudio = transcodeAudio;
        of.transcodeMP3 = transcodeMP3;
        of.transcodeAC3 = transcodeAC3;

        return of;
    }

    public String getLiveOutputFormat() {
        String value = (mAceStreamPreferences != null)
                ? mAceStreamPreferences.getString("output_format_live")
                : null;
        return MiscUtils.ifNull(value, Constants.PREFS_DEFAULT_OUTPUT_FORMAT_LIVE);
    }

    public String getVodOutputFormat() {
        String value = (mAceStreamPreferences != null)
                ? mAceStreamPreferences.getString("output_format_vod")
                : null;
        return MiscUtils.ifNull(value, Constants.PREFS_DEFAULT_OUTPUT_FORMAT_VOD);
    }

    public boolean getTranscodeAudio() {
        return (mAceStreamPreferences != null)
                ? mAceStreamPreferences.getBoolean("transcode_audio", Constants.PREFS_DEFAULT_TRANSCODE_AUDIO)
                : Constants.PREFS_DEFAULT_TRANSCODE_AUDIO;
    }

    public boolean getTranscodeAC3() {
        return (mAceStreamPreferences != null)
                ? mAceStreamPreferences.getBoolean("transcode_ac3", Constants.PREFS_DEFAULT_TRANSCODE_AC3)
                : Constants.PREFS_DEFAULT_TRANSCODE_AC3;
    }

    public boolean showDebugInfo() {
        return mAceStreamPreferences != null && mAceStreamPreferences.getBoolean(PREF_KEY_SHOW_DEBUG_INFO, false);
    }

    public void forgetSelectedPlayer() {
        Logger.vv(TAG, "forgetSelectedPlayer");
        if(mAceStreamPreferences != null) {
            mAceStreamPreferences.putString(Constants.PREF_KEY_SELECTED_PLAYER, null);
        }
        sendMessage(obtainMessage(MSG_FORGET_SELECTED_PLAYER));
    }

    public SelectedPlayer getSelectedPlayer() {
        if(mAceStreamPreferences == null) {
            Log.e(TAG, "getSelectedPlayer: missing manager");
            return null;
        }

        String data = mAceStreamPreferences.getString(Constants.PREF_KEY_SELECTED_PLAYER, null);
        if(data == null) {
            Log.v(TAG, "getSelectedPlayer: no data in prefs");
            return null;
        }

        SelectedPlayer player = null;
        try {
            player = SelectedPlayer.fromJson(data);
        }
        catch(JSONException e) {
            Log.e(TAG, "failed to deserialize player", e);
        }

        return player;
    }

    public void saveSelectedPlayer(@Nullable SelectedPlayer player, boolean fromUser) {
        Logger.vv(TAG, "saveSelectedPlayer: player=" + player + " fromUser=" + fromUser);

        if(player == null) {
            forgetSelectedPlayer();
            return;
        }

        // save locally
        if(mAceStreamPreferences != null) {
            mAceStreamPreferences.putString(Constants.PREF_KEY_SELECTED_PLAYER, player.toJson());
        }

        // send to remote service
        Message msg = obtainMessage(MSG_SAVE_SELECTED_PLAYER);
        Bundle data = new Bundle(2);
        data.putString(MSG_PARAM_SELECTED_PLAYER, player.toJson());
        data.putBoolean(MSG_PARAM_FROM_USER, fromUser);
        msg.setData(data);
        sendMessage(msg);
    }

    public void showBonusAds(final Context context) {
        Logger.vv(TAG, "showBonusAds");
        int authLevel = getAuthLevel();
        if(authLevel == 0) {
            // user is not registered
            new AlertDialog.Builder(context)
                    .setMessage(R.string.sign_in_to_get_bonuses)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            AceStream.openLoginActivity(context, AceStream.LOGIN_TARGET_BONUS_ADS);
                            sendMessage(obtainMessage(MSG_SHOW_BONUS_ADS));
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }
        else {
            // user is registered
            AceStream.openBonusAdsActivity(context);
            sendMessage(obtainMessage(MSG_SHOW_BONUS_ADS));
        }
    }

    public boolean areBonusAdsAvailable() {
        return mBonusAdsAvailable;
    }

    public boolean isMobileNetworkingEnabled() {
        return (mAceStreamPreferences != null)
                && mAceStreamPreferences.getBoolean("mobile_network_available", false);
    }

    public void setMobileNetworkingEnabled(boolean value) {
        setPreference("mobile_network_available", value);
    }

    public void setLocale(String value) {
        setPreference("language", value);
    }

    @Nullable
    public static AceStreamManager getInstance() {
        return sInstance;
    }
}
