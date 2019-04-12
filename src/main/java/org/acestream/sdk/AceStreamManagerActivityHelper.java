package org.acestream.sdk;

import android.content.Context;
import android.util.Log;

import androidx.annotation.MainThread;

public class AceStreamManagerActivityHelper implements AceStreamManager.Client.Callback {
    final private Helper mHelper;
    final protected ActivityCallback mActivity;
    protected boolean mPaused = true;
    protected AceStreamManager mPlaybackManager;

    public interface ActivityCallback {
        void onResumeConnected();
        void onConnected(AceStreamManager service);
        void onDisconnected();
    }

    public AceStreamManagerActivityHelper(Context ctx, ActivityCallback activity) {
        mActivity = activity;
        mHelper = new Helper(ctx, this);
    }

    public void onStart() {
        mHelper.onStart();
    }

    public void onStop() {
        mHelper.onStop();
    }

    public void onResume() {
        mPaused = false;
        if(mPlaybackManager != null) {
            mActivity.onResumeConnected();
        }
    }

    public void onPause() {
        mPaused = true;
    }

    public Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(AceStreamManager service) {
        mPlaybackManager = service;
        mActivity.onConnected(service);
        if(!mPaused) {
            mActivity.onResumeConnected();
        }
    }

    @Override
    public void onDisconnected() {
        mPlaybackManager = null;
        mActivity.onDisconnected();
    }

    public static class Helper {
        private final static String TAG = "AceStream/Helper";

        //private List<PlaybackService.Client.Callback> mFragmentCallbacks = new ArrayList<PlaybackService.Client.Callback>();
        final private AceStreamManager.Client.Callback mActivityCallback;
        private Context mContext;
        private AceStreamManager.Client mClient;
        protected AceStreamManager mService;

        public Helper(Context context, AceStreamManager.Client.Callback activityCallback) {
            mContext = context;
            mClient = new AceStreamManager.Client(context, mClientCallback);
            mActivityCallback = activityCallback;
        }

        @MainThread
        public void onStart() {
            Log.d(TAG, "onStart: context=" + mContext);
            mClient.connect();
        }

        @MainThread
        public void onStop() {
            Log.d(TAG, "onStop: context=" + mContext);
            mClientCallback.onDisconnected();
            mClient.disconnect();
        }

        private final  AceStreamManager.Client.Callback mClientCallback = new AceStreamManager.Client.Callback() {
            @Override
            public void onConnected(AceStreamManager service) {
                mService = service;
                mActivityCallback.onConnected(service);
            }

            @Override
            public void onDisconnected() {
                mService = null;
                mActivityCallback.onDisconnected();
            }
        };
    }
}
