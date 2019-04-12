package org.acestream.sdk;

public interface EngineSessionStartListener {
    void onSuccess(EngineSession session);
    void onError(String error);
}
