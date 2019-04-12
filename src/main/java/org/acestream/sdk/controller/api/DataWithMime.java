package org.acestream.sdk.controller.api;

public class DataWithMime {
    private String mData;
    private String mMime;

    public DataWithMime(String data, String mime) {
        mData = data;
        mMime = mime;
    }

    public String getData() {
        return mData;
    }

    public String getMime() {
        return mMime;
    }
}
