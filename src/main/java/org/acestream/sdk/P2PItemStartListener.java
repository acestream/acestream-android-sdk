package org.acestream.sdk;

public interface P2PItemStartListener {
    void onSessionStarted(EngineSession session);
    void onPrebufferingDone();
    void onError(String error);
}
