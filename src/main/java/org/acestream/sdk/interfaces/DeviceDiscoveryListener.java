package org.acestream.sdk.interfaces;

import org.acestream.sdk.RemoteDevice;

public interface DeviceDiscoveryListener {
    void onDeviceAdded(RemoteDevice device);
    void onDeviceRemoved(RemoteDevice device);
    void onCurrentDeviceChanged(RemoteDevice device);
    boolean canStopDiscovery();
}
