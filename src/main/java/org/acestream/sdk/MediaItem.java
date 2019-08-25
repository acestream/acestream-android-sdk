package org.acestream.sdk;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.controller.Callback;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;

import java.util.Locale;

public class MediaItem {
    private final static String TAG = "AS/MediaItem";

    private Context mContext;
    private MediaFilesResponse.MediaFile mMediaFile;
    private TransportFileDescriptor mDescriptor = null;
    private Uri mUri;
    private Uri mPlaybackUri = null;
    private String mTitle;
    private UpdateListener mUpdateListener;
    private long mSavedTime = 0;
    private long mDuration = 0;
    private String mInfohash = null;
    private int mFileIndex = 0;
    private EngineSession mEngineSession = null;
    private int mEngineSessionId = -1;
    private P2PItemStartListener mEngineSessionListener = null;
    private String mUserAgent = null;
    private long mId = 0;
    private int mIsLive = -1;

    public interface UpdateListener {
        void onTitleChange(MediaItem item, String title);
        void onP2PInfoChanged(MediaItem item, String infohash, int fileIndex);
        void onLiveChanged(MediaItem item, int live);
    }

    private AceStreamManager.PlaybackStateCallback mPlaybackStateCallback = new AceStreamManager.PlaybackStateCallback() {
        @Override
        public void onPlaylistUpdated() {
        }

        @Override
        public void onStart(@Nullable EngineSession session) {
        }

        @Override
        public void onPrebuffering(@Nullable EngineSession session, int progress) {
        }

        @Override
        public void onPlay(@Nullable EngineSession session) {
            if(mEngineSession == null) {
                if(BuildConfig.DEBUG) {
                    Log.v(TAG, "pstate:play: no current engine session");
                }
                return;
            }

            if(session == null) {
                if(BuildConfig.DEBUG) {
                    Log.v(TAG, "pstate:play: null engine session");
                }
                return;
            }

            if(!TextUtils.equals(mEngineSession.playbackSessionId, session.playbackSessionId)) {
                if(BuildConfig.DEBUG) {
                    Log.v(TAG, "pstate:play: session id mismatch: this=" + mEngineSession.playbackSessionId + " that=" + session.playbackSessionId);
                }
                return;
            }

            if(BuildConfig.DEBUG) {
                Log.v(TAG, "pstate:play");
            }

            if(mEngineSessionListener != null) {
                mEngineSessionListener.onPrebufferingDone();
            }
        }

        @Override
        public void onStop() {
        }
    };

    public MediaItem(Context context,
                     Uri uri,
                     String title,
                     long id,
                     TransportFileDescriptor descriptor,
                     MediaFilesResponse.MediaFile mediaFile,
                     UpdateListener listener) {
        mContext = context;
        mUri = uri;
        mTitle = title;
        mId = id;
        mDescriptor = descriptor;
        mMediaFile = mediaFile;
        mUpdateListener = listener;
    }

    public Uri getUri() {
        return mUri;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

    public Uri getPlaybackUri() {
        return isP2PItem() ? mPlaybackUri : mUri;
    }

    public void setPlaybackUri(Uri uri) {
        mPlaybackUri = uri;
    }

    public TransportFileDescriptor getDescriptor() throws TransportFileParsingException {
        if(mDescriptor == null) {
            Log.v(TAG, "getDescriptor: no descriptor, parse from uri");
            if(mUri == null) {
                throw new TransportFileParsingException("missing descriptor and uri");
            }
            mDescriptor = TransportFileDescriptor.fromMrl(mContext.getContentResolver(), mUri);
        }
        return mDescriptor;
    }

    public MediaFilesResponse.MediaFile getMediaFile() {
        return mMediaFile;
    }

    public long getSavedTime() {
        return mSavedTime;
    }

    public void setSavedTime(long value) {
        mSavedTime = value;
    }

    public long getDuration() {
        return mDuration;
    }

    public void setDuration(long value) {
        mDuration = value;
    }

    public void setP2PInfo(String infohash, int fileIndex) {
        mInfohash = infohash;
        mFileIndex = fileIndex;
        if(mUpdateListener != null) {
            mUpdateListener.onP2PInfoChanged(this, infohash, fileIndex);
        }
    }

    public void setLive(boolean live) {
        mIsLive = live ? 1 : 0;
        if(mUpdateListener != null) {
            mUpdateListener.onLiveChanged(this, mIsLive);
        }
    }

    public String getTitle() {
        return mTitle;
    }

    public long getId() {
        return mId;
    }

    public boolean isP2PItem() {
        return TextUtils.equals(mUri.getScheme(), "acestream");
    }

    public boolean isLive() {
        return mMediaFile != null && mMediaFile.isLive();
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "<MediaItem: p2p=%b uri=%s puri=%s this=%d title=%s>",
                isP2PItem(),
                mUri,
                mPlaybackUri,
                this.hashCode(),
                getTitle());
    }

    public void resetP2PItem(IAceStreamManager playbackManager) {
        mPlaybackUri = null;
        mEngineSession = null;
        mEngineSessionId = -1;
        setUserAgent(null);
        if(playbackManager != null) {
            playbackManager.removePlaybackStateCallback(mPlaybackStateCallback);
        }
    }

    public void startP2P(
            @NonNull final IAceStreamManager manager,
            final int[] nextFileIndexes,
            final int streamIndex,
            final boolean restartSessionWithOriginalInitiator,
            @Nullable final String productKey,
            @NonNull final P2PItemStartListener listener) {
        mEngineSessionListener = listener;

        final TransportFileDescriptor descriptor;
        try {
            descriptor = getDescriptor();
        }
        catch(TransportFileParsingException e) {
            Log.e(TAG, "Failed to read transport file", e);
            listener.onError(e.getMessage());
            return;
        }

        if(mMediaFile == null) {
            Log.v(TAG, "startP2P: no media file, get from engine");
            if(mUri == null) {
                throw new IllegalStateException("missing descriptor and MRL");
            }

            final int fileIndex = getP2PFileIndex();

            manager.getEngine(new IAceStreamManager.EngineStateCallback() {
                @Override
                public void onEngineConnected(final @NonNull IAceStreamManager manager, @NonNull EngineApi engineApi) {
                    engineApi.getMediaFiles(descriptor, new Callback<MediaFilesResponse>() {
                        @Override
                        public void onSuccess(MediaFilesResponse result) {
                            for(MediaFilesResponse.MediaFile mf: result.files) {
                                if(mf.index == fileIndex) {
                                    mMediaFile = mf;
                                    setTitle(mf.filename);
                                    startP2P(manager, nextFileIndexes, streamIndex, restartSessionWithOriginalInitiator, productKey, listener);
                                    return;
                                }
                            }
                            Log.e(TAG, "Bad file index: index=" + fileIndex);
                        }

                        @Override
                        public void onError(String err) {
                            listener.onError(err);
                        }
                    });
                }
            });

            return;
        }

        PlaybackData pb = new PlaybackData();
        pb.mediaFile = mMediaFile;
        pb.descriptor = descriptor;
        pb.streamIndex = streamIndex;
        pb.nextFileIndexes = nextFileIndexes;

        pb.outputFormat = manager.getOutputFormatForContent(
                mMediaFile.type,
                mMediaFile.mime,
                null,
                false,
                true);
        pb.useFixedSid = true;
        pb.stopPrevReadThread = 1;
        pb.resumePlayback = false;
        pb.useTimeshift = true;
        pb.keepOriginalSessionInitiator = restartSessionWithOriginalInitiator;
        pb.productKey = productKey;

        manager.addPlaybackStateCallback(mPlaybackStateCallback);
        mEngineSessionId = manager.initEngineSession(pb, new EngineSessionStartListener() {
            @Override
            public void onSuccess(EngineSession session) {
                if(session.clientSessionId == mEngineSessionId) {
                    Logger.v(TAG, "startP2P: session started: " + session);
                    mPlaybackUri = Uri.parse(session.playbackUrl);
                    mEngineSession = session;
                    listener.onSessionStarted(session);
                }
                else {
                    Logger.v(TAG, "startP2P: old session started: expectedId=" + mEngineSessionId + " session=" + session);
                }
            }

            @Override
            public void onError(String error) {
                listener.onError(error);
            }
        });
    }

    public int getP2PFileIndex() {
        if(mMediaFile != null) {
            return mMediaFile.index;
        }

        return MiscUtils.getFileIndex(mUri);
    }

    public void setUserAgent(String value) {
        mUserAgent = value;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public void setTitle(String title) {
        if(!TextUtils.equals(title, mTitle)) {
            mTitle = title;
            if(mUpdateListener != null) {
                mUpdateListener.onTitleChange(this, title);
            }
        }
    }
}
