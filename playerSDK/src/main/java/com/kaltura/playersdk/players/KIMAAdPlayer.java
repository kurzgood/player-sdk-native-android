package com.kaltura.playersdk.players;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;
import com.google.android.libraries.mediaframework.layeredvideo.SimpleVideoPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by nissopa on 7/2/15.
 */
public class KIMAAdPlayer implements VideoAdPlayer, ExoplayerWrapper.PlaybackListener{
    private ViewGroup mAdUIContainer;
    private String mAdMimeType;
    private int mAdPreferedBitrate;
    private FrameLayout mPlayerContainer;
    private Activity mActivity;
    private SimpleVideoPlayer mAdPlayer;
    private KReadinessState mReadiness = KReadinessState.IDLE;
    private KIMAAdPlayerEvents mListener;
    private String mSrc;
    private boolean isSeeking;
    private int currentPosition;
    private final List<VideoAdPlayerCallback> mAdCallbacks =
            new ArrayList<VideoAdPlayerCallback>(1);
    private static final long PLAYHEAD_UPDATE_INTERVAL = 200;
    private static final String TAG = "KIMAAdPlayer";
    @NonNull
    private Handler mPlaybackTimeReporter = new Handler(Looper.getMainLooper());


    // [START VideoAdPlayer region]
    @Override
    public void playAd() {
        if (mAdPlayer != null) {
            mAdPlayer.play();
            startPlaybackTimeReporter();
        }
    }

    @Override
    public void loadAd(String s) {
        setAdPlayerSource(s);
    }

    @Override
    public void stopAd() {
        if (mAdPlayer != null) {
            mAdPlayer.pause();
        }
    }

    @Override
    public void pauseAd() {
        if (mAdPlayer != null) {
            stopPlaybackTimeReporter();
            mAdPlayer.pause();
        }
    }

    @Override
    public void resumeAd() {
        if (mAdPlayer != null) {
            mAdPlayer.play();
        }
    }

    @Override
    public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
        mAdCallbacks.add(videoAdPlayerCallback);
    }

    @Override
    public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
        mAdCallbacks.remove(videoAdPlayerCallback);
    }

    @Override
    public VideoProgressUpdate getAdProgress() {
        if (mAdPlayer == null || mAdPlayer.getDuration() <= 0) {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        }
        return new VideoProgressUpdate(mAdPlayer.getCurrentPosition(), mAdPlayer.getDuration());
    }

    private void startPlaybackTimeReporter() {
        mPlaybackTimeReporter.removeMessages(0); // Stop reporter if already running
        mPlaybackTimeReporter.post(new Runnable() {
            @Override
            public void run() {
                if (mAdPlayer != null) {
                    maybeReportPlaybackTime();
                    mPlaybackTimeReporter.postDelayed(this, PLAYHEAD_UPDATE_INTERVAL);
                }
            }
        });
    }

    private void stopPlaybackTimeReporter() {
        Log.d(TAG, "remove handler callbacks");
        mPlaybackTimeReporter.removeMessages(0);
    }

    private void maybeReportPlaybackTime() {
        if (mListener != null) {
            mListener.adDidProgress((float)mAdPlayer.getCurrentPosition() / 1000, (float)mAdPlayer.getDuration() / 1000);
        }
    }
    // [END VideoAdPlayer region]

    // [START ExoplayerWrapper.PlaybackListener region]
    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case ExoPlayer.STATE_READY:

                if (playWhenReady) {
                    if (mReadiness != KReadinessState.READY) {
                        mReadiness = KReadinessState.READY;

                        updateAdVideoTrackQuality();
                        mListener.adDurationUpdate((float) mAdPlayer.getDuration() / 1000);
                    }
                    for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                        callback.onPlay();
                    }
                } else if (currentPosition > 0) {
                    mAdPlayer.seek(currentPosition, true);
                    isSeeking = true;
                    currentPosition = 0;
                } else if (isSeeking) {
                    isSeeking = false;
                    mAdPlayer.play();
                } else {
                    for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                        callback.onPause();
                    }
                }
                break;
            case ExoPlayer.STATE_ENDED:
                removeAd();
                for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
                    callback.onEnded();
                }
                mReadiness = KReadinessState.IDLE;
                break;
        }
    }

    private void updateAdVideoTrackQuality() {
        if (KMediaFormat.hls_clear.mimeType.equals(mAdMimeType) && mAdPreferedBitrate != -1) {
            Map<Integer,Integer> videoTrackBitrateMap = mAdPlayer.getAvailableBitrateMap();
            int bitrateIndex = -1;
            List<Integer> videoTrackBitrateSortedKeys = new ArrayList(videoTrackBitrateMap.keySet());
            Collections.sort(videoTrackBitrateSortedKeys);
            int selectTrackStratIndex = 0;
            if (videoTrackBitrateSortedKeys.get(0) == -1) {
                selectTrackStratIndex = 1;
            }

            for (int i = 0; i < videoTrackBitrateSortedKeys.size(); i++) {
                Log.d(TAG, i +"-"+ videoTrackBitrateSortedKeys.size() + " HLS Bitrate[" + i + "] = " + videoTrackBitrateSortedKeys.get(i));
                if (i > selectTrackStratIndex && videoTrackBitrateSortedKeys.get(i) > mAdPreferedBitrate) {
                    bitrateIndex = i - 1;
                    Log.d(TAG, "HLS selected bitrate = " + videoTrackBitrateSortedKeys.get(bitrateIndex));
                    mAdPlayer.changeTrack(ExoplayerWrapper.TYPE_VIDEO, videoTrackBitrateMap.get(videoTrackBitrateSortedKeys.get(bitrateIndex)));
                    break;
                }
                if (i > selectTrackStratIndex && i == videoTrackBitrateSortedKeys.size()-1) {
                    Log.d(TAG, "HLS selected last bitrate = " + videoTrackBitrateSortedKeys.get(i));
                    mAdPlayer.changeTrack(ExoplayerWrapper.TYPE_VIDEO, videoTrackBitrateMap.get(videoTrackBitrateSortedKeys.get(i)));
                    break;
                }
            }
        }
    }

    @Override
    public void onError(Exception e) {
        for (VideoAdPlayer.VideoAdPlayerCallback callback : mAdCallbacks) {
            callback.onError();
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

    }
    // [END ExoplayerWrapper.PlaybackListener region]


    public interface KIMAAdPlayerEvents {
        void adDidProgress(float toTome, float totalTime);
        void adDurationUpdate(float totalTime);
    }

    public KIMAAdPlayer(Activity activity, FrameLayout playerContainer, ViewGroup adUIContainer, String adMimeType, int adPrefaredBitrate) {
        mActivity = activity;
        mPlayerContainer = playerContainer;
        mAdUIContainer = adUIContainer;
        mAdMimeType = adMimeType;
        mAdPreferedBitrate = adPrefaredBitrate;
    }

    public void resume() {
        setAdPlayerSource(mSrc);
    }

    public void pause() {
        if(mAdPlayer != null) {
            currentPosition = mAdPlayer.getCurrentPosition();
        }
        removeAd();
    }

    public void setKIMAAdEventListener(KIMAAdPlayerEvents listener) {
        mListener = listener;
    }

    public ViewGroup getAdUIContainer() {
        return mAdUIContainer;
    }

    private void setAdPlayerSource(String src) {
        mSrc = src;
        Video source = new Video(src.toString(), getVideoType());
        mAdPlayer = new SimpleVideoPlayer(mActivity, mPlayerContainer, source, "", true);
        mAdPlayer.addPlaybackListener(this);
        mPlayerContainer.setVisibility(View.VISIBLE);
        mAdPlayer.moveSurfaceToForeground();
        mAdPlayer.disableSeeking();
        mAdPlayer.hideTopChrome();
    }

    public void removeAd() {
        if (mAdPlayer != null) {
            mAdPlayer.release();
            mAdPlayer.moveSurfaceToBackground();
            mPlayerContainer.setVisibility(View.INVISIBLE);
            mAdPlayer = null;
        }
    }

    public void release() {
        if (mAdPlayer != null) {
            mAdPlayer.pause();
            mAdPlayer.moveSurfaceToBackground();
        }
//        mAdUIContainer = null;
//        mPlayerContainer = null;
    }

    private Video.VideoType getVideoType() {
        String videoFileName = Uri.parse(mSrc).getLastPathSegment();
        switch (videoFileName.substring(videoFileName.lastIndexOf('.')).toLowerCase()) {
            case ".mpd":
                return Video.VideoType.DASH;
            case ".mp4":
                return Video.VideoType.MP4;
            case ".m3u8":
                return Video.VideoType.HLS;
            default:
                return Video.VideoType.OTHER;
        }

    }
}
