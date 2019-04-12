package org.acestream.sdk.interfaces;

public interface ConnectableDeviceListener {
    void onStatus(IRemoteDevice device, int status);
    void onPosition(IRemoteDevice device, Long position);
    void onDuration(IRemoteDevice device, Long duration);
    void onVolume(IRemoteDevice device, Float volume);
}
