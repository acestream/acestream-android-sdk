package org.acestream.sdk.errors;

public class TransportFileParsingException extends Exception {
    private String mMissingFilePath;
    public TransportFileParsingException() {
    }

    public TransportFileParsingException(String error) {
        super(error);
    }

    public TransportFileParsingException(String error, Throwable e) {
        super(error, e);
    }

    public void setMissingFilePath(String value) {
        mMissingFilePath = value;
    }

    public String getMissingFilePath() {
        return mMissingFilePath;
    }

}
