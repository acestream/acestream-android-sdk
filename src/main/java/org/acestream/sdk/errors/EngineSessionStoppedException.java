package org.acestream.sdk.errors;

public class EngineSessionStoppedException extends Exception {
    public EngineSessionStoppedException() {
    }

    public EngineSessionStoppedException(String error) {
        super(error);
    }
}
