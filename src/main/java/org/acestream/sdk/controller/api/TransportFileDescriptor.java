package org.acestream.sdk.controller.api;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.Constants;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class TransportFileDescriptor {
    private final static String TAG = "AS/TFD";

    private String mContentId;
    private String mInfohash;
    private String mUrl;
    private String mMagnet;
    private String mCacheKey;
    private String mLocalPath;
    // exclude from Gson serialization
    private transient String mTransportFileData;

    public static TransportFileDescriptor fromJson(String data) {
        return new Gson().fromJson(data, TransportFileDescriptor.class);
    }

    public static TransportFileDescriptor fromJsonRpcMessage(JsonRpcMessage msg) throws TransportFileParsingException {
        File file = null;
        String descriptorString = null;
        try {

            Builder builder = new Builder();
            descriptorString = msg.getString("contentDescriptor");
            String transportFileData = msg.getString("transportFileData");

            if(transportFileData != null) {
                // Save to local internal file
                File dir = AceStream.getTransportFilesDir(true);
                String filename = null;

                // Need idempotent filename: the same for the same descriptor string
                try {
                    filename = MiscUtils.sha1Hash(transportFileData) + ".torrent";
                } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                    Log.e(TAG, "Failed to create internal file", e);
                }

                if (TextUtils.isEmpty(filename)) {
                    // Fallback to temp file
                    file = File.createTempFile("tf", ".torrent", dir);
                } else {
                    file = new File(dir, filename);
                }
                Logger.v(TAG, "fromJsonRpcMessage:transform: descriptor=" + descriptorString + " path=" + file.getAbsolutePath() + " exists=" + file.exists());
                FileOutputStream output = new FileOutputStream(file);
                output.write(Base64.decode(transportFileData, Base64.DEFAULT));
                output.close();

                builder.setLocalFile(file.getAbsolutePath());
            }
            else if(descriptorString != null) {
                parseMrl(Uri.parse("acestream:?" + descriptorString), builder, false, null);
            }
            else {
                throw new TransportFileParsingException("missing both descriptor string and transport file data");
            }

            return builder.build();
        }
        catch(IOException e) {
            TransportFileParsingException ex = new TransportFileParsingException("Got IOException: descriptor=" + descriptorString, e);
            if(e instanceof FileNotFoundException && file != null) {
                ex.setMissingFilePath(file.getAbsolutePath());
            }
            throw ex;
        }
    }

    public static TransportFileDescriptor fromMrl(@NonNull ContentResolver resolver, @NonNull Uri mrl) throws TransportFileParsingException {
        if(!TextUtils.equals(mrl.getScheme(), "acestream")) {
            throw new TransportFileParsingException("unknown scheme: " + mrl.getScheme());
        }
        Builder builder = new Builder();
        parseMrl(mrl, builder, true, resolver);
        return builder.build();
    }

    private static void parseMrl(Uri mrl, Builder builder, boolean readLocalFile, ContentResolver resolver) throws TransportFileParsingException {
        boolean gotDescriptor = false;

        mrl = processAceStreamUri(mrl);

        if(!TextUtils.equals(mrl.getScheme(), "acestream")) {
            throw new TransportFileParsingException("unknown scheme: " + mrl.getScheme());
        }

        Map<String, String> params;
        try {
            params = MiscUtils.getQueryParameters(mrl);
        }
        catch(Exception e) {
            throw new TransportFileParsingException("failed to parse mrl: " + mrl, e);
        }

        for(Map.Entry<String, String> item: params.entrySet()) {
            switch(item.getKey()) {
                case "data":
                    if(readLocalFile) {
                        try {
                            if (item.getValue().startsWith("content:")) {
                                builder.setContentUri(resolver, Uri.parse(item.getValue()));
                            } else if (item.getValue().startsWith("file:")) {
                                builder.setContentUri(resolver, Uri.parse(item.getValue()));
                            } else if (item.getValue().startsWith("/")) {
                                builder.setLocalFile(item.getValue());
                            } else if (item.getValue().startsWith("http:") || item.getValue().startsWith("https:")) {
                                builder.setUrl(item.getValue());
                            } else {
                                throw new TransportFileParsingException("Cannot parse local file URI: mrl=" + mrl + " uri=" + item.getValue());
                            }
                        }
                        catch(IOException e) {
                            TransportFileParsingException ex = new TransportFileParsingException("Got IOException: mrl=" + mrl, e);
                            if(e instanceof FileNotFoundException) {
                                ex.setMissingFilePath(item.getValue());
                            }
                            throw ex;
                        }
                    }
                    else {
                        builder.setLocalPath(item.getValue());
                    }
                    gotDescriptor = true;
                    break;
                case "content_id":
                    builder.setContentId(item.getValue());
                    gotDescriptor = true;
                    break;
                case "magnet":
                    builder.setMagnet(item.getValue());
                    gotDescriptor = true;
                    break;
                case "url":
                    builder.setUrl(item.getValue());
                    gotDescriptor = true;
                    break;
                case "infohash":
                    builder.setInfohash(item.getValue());
                    gotDescriptor = true;
                    break;
            }
        }

        if(!gotDescriptor) {
            throw new TransportFileParsingException("missing descriptor: mrl=" + mrl);
        }
    }

    public static TransportFileDescriptor fromContentUri(
            @NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
        Builder builder = new Builder();
        builder.setContentUri(resolver, uri);
        return builder.build();
    }

    public static TransportFileDescriptor fromFile(String path) throws IOException {
        Builder builder = new Builder();
        builder.setLocalFile(path);
        return builder.build();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public void toJsonRpcMessage(JsonRpcMessage msg) {
        // update message with param
        msg.addParam("contentDescriptor", getDescriptorString());
        if(mTransportFileData != null) {
            msg.addParam("transportFileData", mTransportFileData);
        }
    }

    public String getDescriptorString() {
        String ds;

        if(mContentId != null) {
            ds = "content_id=" + Uri.encode(mContentId);
        }
        else if(mMagnet != null) {
            ds = "magnet=" + Uri.encode(mMagnet);
        }
        else if(mUrl != null) {
            ds = "url=" + Uri.encode(mUrl);
        }
        else if(mTransportFileData != null && mInfohash == null) {
            if(mLocalPath == null) {
                throw new IllegalStateException("missing local path");
            }
            ds = "data=" + Uri.encode(mLocalPath);
        }
        else if(mLocalPath != null) {
            // Need this for backward compatibility with old AceCast remote controls.
            ds = "data=" + Uri.encode(mLocalPath);
        }
        else if(mInfohash != null) {
            ds = "infohash=" + Uri.encode(mInfohash);
        }
        else {
            throw new IllegalStateException("missing content descriptor: " + dump());
        }

        return ds;
    }

    public Uri getMrl(int fileIndex) {
        return Uri.parse("acestream:?" + getDescriptorString() + "&index=" + fileIndex);
    }

    public String getContentId() {
        return mContentId;
    }

    public String getInfohash() {
        return mInfohash;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getMagnet() {
        return mMagnet;
    }

    public String getCacheKey() {
        return mCacheKey;
    }

    public String getLocalPath() {
        return mLocalPath;
    }

    public boolean isInternal() {
        if(mLocalPath == null) return false;
        File dir = AceStream.context().getFilesDir();
        return mLocalPath.startsWith(dir.toString());
    }

    public String getTransportFileData() {
        return mTransportFileData;
    }

    public void setTransportFileData(String data) {
        mTransportFileData = data;
    }

    public boolean hasTransportFileData() {
        return !TextUtils.isEmpty(getTransportFileData());
    }

    public void fetchTransportFileData(final ContentResolver resolver) throws TransportFileParsingException {
        try {
            if (!hasTransportFileData()) {
                String data = AceStream.getTransportFileFromCache(getDescriptorString());
                if (!TextUtils.isEmpty(data)) {
                    setTransportFileData(data);
                } else if (!TextUtils.isEmpty(mLocalPath)) {
                    if (mLocalPath.startsWith("file:")) {
                        mTransportFileData = Base64.encodeToString(
                                MiscUtils.readBytesFromContentUri(resolver, Uri.parse(mLocalPath)),
                                Base64.DEFAULT);
                    } else {
                        mTransportFileData = Base64.encodeToString(
                                MiscUtils.readBytesFromFile(mLocalPath),
                                Base64.DEFAULT);
                    }
                }
                else {
                    Log.v(TAG,"fetchTransportFileData: missing local path: descriptor=" + getDescriptorString());
                }
            }
        }
        catch(IOException e) {
            throw new TransportFileParsingException("Got IOException: descriptor=" + getDescriptorString(), e);
        }
    }

    public void setCacheKey(String key) {
        mCacheKey = key;
    }

    public void setInfohash(String infohash) {
        mInfohash = infohash;
    }

    public String getQueryString() {
        String query;

        if(mTransportFileData != null) {
            // data is sent in POST payload
            query = "";
        }
        else if(mContentId != null) {
            query = "content_id=" + Uri.encode(mContentId);
        }
        else if(mUrl != null) {
            query = "url=" + Uri.encode(mUrl);
        }
        else if(mLocalPath != null) {
            query = "url=" + Uri.encode(mLocalPath);
        }
        else if(mMagnet != null) {
            query = "magnet=" + Uri.encode(mMagnet);
        }
        else if(mInfohash != null) {
            query = "infohash=" + Uri.encode(mInfohash);
        }
        else {
            throw new IllegalStateException("missing content descriptor");
        }

        if(mCacheKey != null) {
            if(query.length() != 0) {
                query += "&";
            }
            query += "transport_file_cache_key=" + Uri.encode(mCacheKey);
        }

        return query;
    }

    public boolean shouldPost() {
        return mTransportFileData != null;
    }

    public DataWithMime getPostPayload() {
        if(mTransportFileData == null) {
            throw new IllegalStateException("missing transport file data");
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("transport_file_data", mTransportFileData);
        }
        catch(JSONException e) {
            throw new IllegalStateException(e);
        }

        return new DataWithMime(payload.toString(), Constants.MIME_JSON);
    }

    public static class Builder {
        private TransportFileDescriptor mDescriptor;

        public Builder() {
            mDescriptor = new TransportFileDescriptor();
        }

        public Builder setContentId(String contentId) {
            mDescriptor.mContentId = contentId;
            return this;
        }

        public Builder setInfohash(String infohash) {
            mDescriptor.mInfohash = infohash;
            return this;
        }

        public Builder setMagnet(String magnet) {
            mDescriptor.mMagnet = magnet;
            return this;
        }

        public Builder setUrl(String url) {
            mDescriptor.mUrl = url;
            return this;
        }

        public Builder setTransportFileData(String transportFileData) {
            mDescriptor.mTransportFileData = transportFileData;
            return this;
        }

        public Builder setCacheKey(String cacheKey) {
            mDescriptor.mCacheKey = cacheKey;
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder setLocalPath(@NonNull String path) {
            mDescriptor.mLocalPath = path;
            return this;
        }

        public Builder setLocalFile(@NonNull String path) throws IOException {
            setLocalPath(path);
            mDescriptor.mTransportFileData = Base64.encodeToString(MiscUtils.readBytesFromFile(path), Base64.DEFAULT);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder setContentUri(@NonNull ContentResolver resolver, @NonNull Uri uri) throws IOException {
            if(TextUtils.equals(uri.getScheme(), "content")) {
                File dir = AceStream.getTransportFilesDir(true);
                File file;
                String filename;

                // Need idempotent filename: the same for the same content:// URI
                try {
                    filename = MiscUtils.sha1Hash(uri.toString()) + ".torrent";
                }
                catch(NoSuchAlgorithmException e) {
                    filename = uri.getLastPathSegment();
                }

                if(TextUtils.isEmpty(filename)) {
                    // Fallback to temp file
                    file = File.createTempFile("tf", ".torrent", dir);
                }
                else {
                    file = new File(dir, filename);
                }
                Logger.v(TAG, "setContentUri:transform: uri=" + uri + " path=" + file.getAbsolutePath() + " exists=" + file.exists());
                FileOutputStream output = new FileOutputStream(file);
                output.write(MiscUtils.readBytesFromContentUri(resolver, uri));
                output.close();
                return setLocalFile(file.getAbsolutePath());
            }

            setLocalPath(uri.toString());
            // Always first try to read from cache
            String data = AceStream.getTransportFileFromCache(mDescriptor.getDescriptorString());
            if(data != null) {
                mDescriptor.mTransportFileData = data;
            }
            else {
                try {
                    mDescriptor.mTransportFileData = Base64.encodeToString(MiscUtils.readBytesFromContentUri(resolver, uri), Base64.DEFAULT);
                }
                catch(OutOfMemoryError e) {
                    throw new IOException("Failed to read from URI", e);
                }
            }
            return this;
        }

        public TransportFileDescriptor build() {
            return mDescriptor;
        }
    }

    public boolean equals(TransportFileDescriptor other) {
        if(other == null) {
            return false;
        }
        else if(TextUtils.equals(other.getDescriptorString(), this.getDescriptorString())) {
            return true;
        }
        else if(other.mLocalPath != null && TextUtils.equals(other.mLocalPath, this.mLocalPath)) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Detect acestream URI and convert it if needed.
     * Non-acestream URIs are returned without modification.
     *
     * @param uri
     * @return Uri
     */
    public static Uri processAceStreamUri(Uri uri) {
        if(uri == null) {
            return null;
        }

        // Accept plain content id
        if(Pattern.matches("^[0-9a-f]{40}$", uri.toString().toLowerCase())) {
            uri = Uri.parse("acestream:?content_id=" + uri.toString());
            Logger.v(TAG, "processAceStreamUri: convert plain content id: mrl=" + uri);
        }

        // Convert magnet:
        // magnet:?xt=urn:btih:c38c16d317f0e7276476d94e1726833804425c58 -> acestream:?magnet=magnet%3A%3Fxt%3Durn%3Abtih%3Ac38c16d317f0e7276476d94e1726833804425c58
        if(TextUtils.equals(uri.getScheme(), "magnet")) {
            uri = Uri.parse("acestream:?magnet=" + Uri.encode(uri.toString()));
            Logger.v(TAG, "processAceStreamUri: convert magnet: mrl=" + uri);
        }

        if(uri.toString().startsWith("acestream:%3F")) {
            // Fix encoded "?" (which could be encoded by error)
            uri = Uri.parse("acestream:?" + uri.toString().substring(13));
            Logger.v(TAG, "processAceStreamUri: convert encoded '?': mrl=" + uri);
        }

        // Convert old format:
        // acestream://94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209 -> acestream:?content_id=94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209
        if(Pattern.matches("^acestream://[0-9a-f]{40}$", uri.toString().toLowerCase())) {
            uri = Uri.parse("acestream:?content_id=" + uri.toString().substring(12));
            Logger.v(TAG, "processAceStreamUri: convert old content id format: mrl=" + uri);
        }

        if(!TextUtils.equals(uri.getScheme(), "acestream") && uri.toString().endsWith(".torrent")) {
            uri = Uri.parse("acestream:?data=" + Uri.encode(uri.toString()));
            try {
                TransportFileDescriptor descriptor = TransportFileDescriptor.fromMrl(AceStream.context().getContentResolver(), uri);
                uri = descriptor.getMrl(0);
            }
            catch(TransportFileParsingException e) {
                Log.e(TAG, "processAceStreamUri: error", e);
            }
        }

        return uri;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "<TFD: desc=%s data=%b>",
                getDescriptorString(),
                !TextUtils.isEmpty(mTransportFileData));
    }

    private String dump() {
        return String.format(Locale.getDefault(),
                "content_id=%s infohash=%s url=%s magnet=%s path=%s data=%b>",
                mContentId,
                mInfohash,
                mUrl,
                mMagnet,
                mLocalPath,
                !TextUtils.isEmpty(mTransportFileData));
    }
}
