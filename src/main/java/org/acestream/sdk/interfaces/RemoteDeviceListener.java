package org.acestream.sdk.interfaces;

import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.RemoteDevice;
import org.acestream.sdk.SelectedPlayer;

public interface RemoteDeviceListener {
    void onConnected(RemoteDevice device);
    void onDisconnected(RemoteDevice device, boolean cleanShutdown);
    void onMessage(RemoteDevice device, JsonRpcMessage msg);
    void onAvailable(RemoteDevice device);
    void onUnavailable(RemoteDevice device);
    void onPingFailed(RemoteDevice device);
    void onOutputFormatChanged(RemoteDevice device, String outputFormat);
    void onSelectedPlayerChanged(RemoteDevice device, SelectedPlayer player);
}
