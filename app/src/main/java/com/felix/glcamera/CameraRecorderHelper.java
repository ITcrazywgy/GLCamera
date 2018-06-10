package com.felix.glcamera;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;

import com.felix.glcamera.util.CameraUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
import static android.hardware.Camera.Parameters.WHITE_BALANCE_AUTO;


@SuppressWarnings("ALL")
public class CameraRecorder implements Callback, MediaRecorder.OnErrorListener, SurfaceTexture.OnFrameAvailableListener {

    private static final int ERROR_CAMERA_PREVIEW = 1;
    private static final int ERROR_FILE_CREATE = 2;
    private static final int MAX_FRAME_RATE = 30;

    private Camera mCamera;
    private Parameters mParameters;
    private SurfaceHolder mSurfaceHolder;
    private VideoObject mVideoObject;
    private OnErrorListener mOnErrorListener;
    private OnPreparedListener mOnPreparedListener;
    private int mFrameRate = 15;
    private int mCameraId = CameraInfo.CAMERA_FACING_BACK;
    private boolean mPrepared;
    private boolean mStartPreview;
    private boolean mSurfaceCreated;

    private volatile boolean mRecording;
    private CameraHandler mCameraHandler;


    CameraRecorder() {
        mCameraHandler = new CameraHandler(this);
    }

    private GLSurfaceView mGLSurfaceView;

    private CameraSurfaceRenderer mRenderer;

    @SuppressLint("ObsoleteSdkInt")
    public void setPreviewDisplay(GLSurfaceView glSurfaceView) {
        this.mGLSurfaceView = glSurfaceView;
        mGLSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new CameraSurfaceRenderer(this);
        mGLSurfaceView.setRenderer(mRenderer);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void prepare() {
        this.mPrepared = true;
        if (this.mSurfaceCreated) {
            this.startPreview();
        }
    }


    public void setOutputDirectory(String outputDirectory, String key) {
        if (!TextUtils.isEmpty(outputDirectory)) {
            File outputDir = new File(outputDirectory);
            deleteFileIfExists(outputDir);
            boolean result = outputDir.mkdirs();
            if (result) {
                this.mVideoObject = new VideoObject(outputDirectory, key);
            } else {
                mOnErrorListener.onVideoError(ERROR_FILE_CREATE, 0);
            }
        }
    }

    void start() {
        if (this.mVideoObject != null && this.mSurfaceHolder != null && !this.mRecording) {
            try {
                mVideoEncoder = new TextureMovieEncoder();
                this.mRecording = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        if (this.mVideoEncoder != null && mRecording) {
            this.mMediaRecorder.setOnErrorListener(null);
            this.mMediaRecorder.setPreviewDisplay(null);
            try {
                this.mVideoEncoder.stop();
                this.mVideoEncoder.release();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (RuntimeException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (this.mCamera != null) {
            try {
                this.mCamera.lock();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        this.mRecording = false;
        this.stopPreview();
        this.mPrepared = false;
        this.mVideoEncoder = null;
    }

    public void release() {

    }


    void setOnPreparedListener(OnPreparedListener preparedListener) {
        this.mOnPreparedListener = preparedListener;
    }

    void setOnErrorListener(OnErrorListener var1) {
        this.mOnErrorListener = var1;
    }

    boolean isFrontCamera() {
        return this.mCameraId == 1;
    }

    private void switchCamera(int cameraId) {
        switch (cameraId) {
            case CameraInfo.CAMERA_FACING_BACK:
            case CameraInfo.CAMERA_FACING_FRONT:
                this.mCameraId = cameraId;
                this.stopPreview();
                this.startPreview();
            default:
        }
    }

    void switchCamera() {
        if (this.mCameraId == CameraInfo.CAMERA_FACING_BACK) {
            this.switchCamera(CameraInfo.CAMERA_FACING_FRONT);
        } else {
            this.switchCamera(CameraInfo.CAMERA_FACING_BACK);
        }
    }


    void toggleFlashMode() {
        if (this.mParameters != null) {
            try {
                String flashMode;
                if (!TextUtils.isEmpty(flashMode = this.mParameters.getFlashMode()) && !Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    this.setFlashMode(Parameters.FLASH_MODE_OFF);
                } else {
                    this.setFlashMode(Parameters.FLASH_MODE_TORCH);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setFlashMode(String flashMode) {
        if (this.mParameters != null && this.mCamera != null) {
            try {
                if (Parameters.FLASH_MODE_TORCH.equals(flashMode) || Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    this.mParameters.setFlashMode(flashMode);
                    this.mCamera.setParameters(this.mParameters);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    void deleteVideoObject() {
        if (this.mVideoObject != null && !TextUtils.isEmpty(this.mVideoObject.getOutputDirectory())) {
            deleteFileIfExists(new File(this.mVideoObject.getOutputDirectory()));
        }
        this.mVideoObject = null;
    }

    private static final String TAG = "MediaRecorderHelper";

    private void deleteFileIfExists(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    if (subFile.isDirectory()) {
                        deleteFileIfExists(subFile);
                    }
                    boolean delete = subFile.delete();
                    if (!delete) {
                        Log.e(TAG, "error occurred when deleting file");
                    }
                }
            }
        }
        boolean delete = file.delete();
        if (!delete) {
            Log.e(TAG, "error occurred when deleting file");
        }
    }

    private TextureMovieEncoder mVideoEncoder;


    private boolean isSupported(List list, String target) {
        return list != null && list.contains(target);
    }

    private int mPreviewWidth = -1;
    private int mPreviewHeight = -1;

    int getPreviewWidth() {
        return mPreviewWidth;
    }

    int getPreviewHeight() {
        return mPreviewHeight;
    }

    private void prepareCameraParameters() {
        if (this.mParameters != null) {
            //设置预览帧率
            List<Integer> supportedFrameRates = this.mParameters.getSupportedPreviewFrameRates();
            if (supportedFrameRates != null) {
                if (supportedFrameRates.contains(MAX_FRAME_RATE)) {
                    this.mFrameRate = MAX_FRAME_RATE;
                } else {
                    Collections.sort(supportedFrameRates);
                    for (int i = supportedFrameRates.size() - 1; i >= 0; --i) {
                        if (supportedFrameRates.get(i) <= MAX_FRAME_RATE) {
                            this.mFrameRate = supportedFrameRates.get(i);
                            break;
                        }
                    }
                    if (this.mFrameRate == -1) {
                        this.mFrameRate = supportedFrameRates.get(0);
                    }
                }
            }
            this.mParameters.setPreviewFrameRate(this.mFrameRate);

            //设置预览尺寸
            List<Size> supportedPreviewSizes = this.mParameters.getSupportedPreviewSizes();
            int preferPreviewWidth = mGLSurfaceView.getHeight();
            int preferPreviewHeight = mGLSurfaceView.getWidth();
            Size previewSize = CameraUtils.chooseOptimalSize(supportedPreviewSizes, preferPreviewWidth, preferPreviewHeight);
            this.mPreviewWidth = previewSize.width;
            this.mPreviewHeight = previewSize.height;
            this.mParameters.setPreviewSize(this.mPreviewWidth, this.mPreviewHeight);
            //List<Size> supportedVideoSizes = this.mCamera.getParameters().getSupportedVideoSizes();
            //mVideoSize = CameraUtils.chooseOptimalSize(supportedVideoSizes, this.mPreviewWidth, this.mPreviewHeight);

            if (isSupported(this.mParameters.getSupportedFocusModes(), FOCUS_MODE_CONTINUOUS_VIDEO)) {
                this.mParameters.setFocusMode(FOCUS_MODE_CONTINUOUS_VIDEO);
            }

            if (isSupported(this.mParameters.getSupportedWhiteBalance(), WHITE_BALANCE_AUTO)) {
                this.mParameters.setWhiteBalance(WHITE_BALANCE_AUTO);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                if (this.mParameters.isVideoStabilizationSupported()) {
                    this.mParameters.setVideoStabilization(true);
                }
            }

            if (!CameraUtils.isDevice("GT-N7100", "GT-I9308", "GT-I9300")) {
                this.mParameters.set("cam_mode", 1);
                this.mParameters.set("cam-mode", 1);
            }

            if (!CameraUtils.isDevice("GT-I9100"))
                this.mParameters.setRecordingHint(true);
        }
    }

    private void retrieveMetaData() {
        if (mVideoObject != null && !TextUtils.isEmpty(mVideoObject.getVideoPath()) && new File(mVideoObject.getVideoPath()).exists()) {
            MediaMetadataRetriever retr = new MediaMetadataRetriever();
            retr.setDataSource(mVideoObject.getVideoPath());
            String sWidth = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String sHeight = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String sDuration = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mVideoObject.setVideoWidth((int) parseFloat(sWidth));
            mVideoObject.setVideoHeight((int) parseFloat(sHeight));
            mVideoObject.setVideoDuration((int) parseFloat(sDuration));
        }
    }


    private float parseFloat(String sValue) {
        try {
            return (int) Float.parseFloat(sValue);
        } catch (Exception e) {
            return 0;
        }
    }


    public boolean isVideoExists() {
        return mVideoObject != null && new File(mVideoObject.getVideoPath()).exists();
    }

    public int getVideoDuration() {
        if (mVideoObject != null) {
            int duration = mVideoObject.getVideoDuration();
            if (duration != 0) {
                return duration;
            } else {
                retrieveMetaData();
                return mVideoObject.getVideoDuration();
            }
        }
        return 0;
    }

    public int getVideoWidth() {
        if (mVideoObject != null) {
            int videoWidth = mVideoObject.getVideoWidth();
            if (videoWidth != 0) {
                return videoWidth;
            } else {
                retrieveMetaData();
                return mVideoObject.getVideoWidth();
            }
        }
        return 0;
    }

    public int getVideoHeight() {
        if (mVideoObject != null) {
            int videoHeight = mVideoObject.getVideoHeight();
            if (videoHeight != 0) {
                return videoHeight;
            } else {
                retrieveMetaData();
                return mVideoObject.getVideoHeight();
            }
        }
        return 0;
    }


    public String getVideoThumbnail() {
        if (mVideoObject != null) {
            if (TextUtils.isEmpty(mVideoObject.getVideoThumbPath()) || !new File(mVideoObject.getVideoThumbPath()).exists()) {
                if (new File(mVideoObject.getVideoPath()).exists() && createVideoThumbnail(mVideoObject.getVideoPath(), mVideoObject.getVideoThumbPath())) {
                    return mVideoObject.getVideoThumbPath();
                }
            }
        }
        return null;
    }

    public String getVideoPath() {
        if (mVideoObject != null) {
            return mVideoObject.getVideoPath();
        }
        return null;
    }


    private boolean createVideoThumbnail(String videoPath, String videoThumbPath) {
        if (TextUtils.isEmpty(videoThumbPath)) return false;
        File file = new File(videoThumbPath);
        if (file.exists()) {
            return true;
        }
        Bitmap bitmap = null;
        FileOutputStream fos = null;
        try {
            if (!file.getParentFile().exists()) {
                boolean mkdirs = file.getParentFile().mkdirs();
                if (!mkdirs) return false;
            }
            bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, 3);
            if (bitmap == null) return false;
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return true;
    }

    private MediaPlayer mMediaPlayer;

    public void playVideo() {
        try {
            if (mSurfaceHolder == null) return;
            if (mMediaPlayer == null) {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        if (!mMediaPlayer.isPlaying()) {
                            mMediaPlayer.start();
                        }
                    }
                });
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setDisplay(mSurfaceHolder);
                mMediaPlayer.setDataSource(mVideoObject.getVideoPath());
                mMediaPlayer.setLooping(true);
                mMediaPlayer.prepareAsync();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void releasePlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }


    private void startPreview() {
        if (!this.mStartPreview && this.mSurfaceHolder != null && this.mPrepared) {
            this.mStartPreview = true;
            try {
                if (this.mCameraId == CameraInfo.CAMERA_FACING_BACK) {
                    this.mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
                } else {
                    this.mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
                }
                this.mCamera.setDisplayOrientation(90);
                this.mCamera.setPreviewDisplay(this.mSurfaceHolder);
                this.mParameters = this.mCamera.getParameters();
                this.prepareCameraParameters();
                this.mCamera.setParameters(this.mParameters);
                this.mCamera.startPreview();
                if (this.mOnPreparedListener != null) {
                    this.mOnPreparedListener.onPrepared();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (this.mOnErrorListener != null) {
                    this.mOnErrorListener.onVideoError(ERROR_CAMERA_PREVIEW, 0);
                }
            }
        }
    }


    private void stopPreview() {
        if (this.mCamera != null) {
            try {
                this.mCamera.stopPreview();
                this.mCamera.setPreviewCallback(null);
                this.mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.mCamera = null;
        }
        this.mStartPreview = false;
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.mSurfaceHolder = holder;
        this.mSurfaceCreated = true;
        if (isVideoExists()) {
            playVideo();
        } else {
            if (this.mPrepared && !this.mStartPreview) {
                this.startPreview();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        this.mSurfaceHolder = surfaceHolder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        this.mSurfaceHolder = null;
        this.mSurfaceCreated = false;
    }


    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        try {
            if (mr != null) {
                mr.reset();
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (this.mOnErrorListener != null) {
            this.mOnErrorListener.onVideoError(what, extra);
        }
    }


    public interface OnErrorListener {
        void onVideoError(int what, int extra);
    }

    public interface OnPreparedListener {
        void onPrepared();
    }

    public static class VideoObject implements Serializable {
        private static final long serialVersionUID = -3584369940642260675L;
        private String outputDirectory;
        private String outputVideoThumbPath;
        private String outputVideoPath;

        private int videoWidth;
        private int videoHeight;
        private int videoDuration;


        VideoObject(String outputDirectory, String key) {
            this.outputDirectory = outputDirectory;
            this.outputVideoPath = this.outputDirectory + File.separator + key + ".mp4";
            this.outputVideoThumbPath = this.outputDirectory + File.separator + key + "_thumb.jpg";
        }


        String getVideoThumbPath() {
            return outputVideoThumbPath;
        }

        String getOutputDirectory() {
            return outputDirectory;
        }

        String getVideoPath() {
            return outputVideoPath;
        }


        int getVideoWidth() {
            return videoWidth;
        }

        void setVideoWidth(int videoWidth) {
            this.videoWidth = videoWidth;
        }

        int getVideoHeight() {
            return videoHeight;
        }

        void setVideoHeight(int videoHeight) {
            this.videoHeight = videoHeight;
        }

        int getVideoDuration() {
            return videoDuration;
        }

        void setVideoDuration(int videoDuration) {
            this.videoDuration = videoDuration;
        }


    }


    static class CameraHandler extends Handler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;

        private WeakReference<CameraRecorder> mWeakRef;

        public CameraHandler(CameraRecorder activity) {
            mWeakRef = new WeakReference<>(activity);
        }

        public void invalidateHandler() {
            mWeakRef.clear();
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            CameraRecorder recorderHelper = mWeakRef.get();
            if (recorderHelper == null) {
                return;
            }
            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:
                    recorderHelper.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }


    private void handleSetSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(this);
        try {
            mCamera.setPreviewTexture(st);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCamera.startPreview();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        mGLSurfaceView.requestRender();
    }


}
