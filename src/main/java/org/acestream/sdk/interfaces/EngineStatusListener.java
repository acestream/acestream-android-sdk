package org.acestream.sdk.interfaces;

import org.acestream.sdk.EngineStatus;

public interface EngineStatusListener {
    void onEngineStatus(EngineStatus status, IRemoteDevice remoteDevice);
    boolean updatePlayerActivity();
}
