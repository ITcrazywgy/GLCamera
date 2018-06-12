package com.felix.glcamera;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.felix.glcamera.gles.FullFrameRect;
import com.felix.glcamera.gles.Texture2dProgram;
import com.felix.glcamera.util.CameraUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
import static android.hardware.Camera.Parameters.WHITE_BALANCE_AUTO;
import static com.felix.glcamera.FilterType.FILTER_NONE;
import static com.felix.glcamera.FilterType.FILTER_NORMAL;


@SuppressWarnings("ALL")
public class CameraRecorderHelper {

    private static final int ERROR_CAMERA_OPEN = 1;
    private static final int ERROR_CAMERA_EXISTED = 2;
    private static final int ERROR_CAMERA_PREVIEW = 3;
    private static final int ERROR_FILE_CREATE = 4;
    private static final int MAX_FRAME_RATE = 30;

    private Camera mCamera;
    private Parameters mParameters;
    private VideoObject mVideoObject;
    private OnErrorListener mOnErrorListener;
    private OnPreviewListener mOnPreparedListener;
    private int mFrameRate = 15;
    private int mCameraId = CameraInfo.CAMERA_FACING_BACK;
    private boolean mStartPreview;
    private boolean mStartPlay;

    private MediaRecorder mMediaRecorder;

    private GLSurfaceView mGLSurfaceView;

    private CameraSurfaceRenderer mRenderer;

    public void setPreviewDisplay(GLSurfaceView glSurfaceView) {
        this.mGLSurfaceView = glSurfaceView;
        mGLSurfaceView.setEGLContextClientVersion(2);
        mRenderer = new CameraSurfaceRenderer();
        mGLSurfaceView.setRenderer(mRenderer);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }


    public void setOutputDirectory(String outputDirectory, String key) {
        if (!TextUtils.isEmpty(outputDirectory)) {
            File outputDir = new File(outputDirectory);
            deleteFileIfExists(outputDir);
            boolean result = outputDir.mkdirs();
            if (result) {
                this.mVideoObject = new VideoObject(outputDirectory, key);
            } else {
                mOnErrorListener.onError(ERROR_FILE_CREATE, "unable to create file");
            }
        }
    }

    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;

    public void startRecord() {
        if (!isRecording) {
            mMediaRecorder = new MediaRecorder(mGLSurfaceView);
            mMediaRecorder.setOutputFile(this.getVideoPath());
            mMediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024);
            mMediaRecorder.setVideoSize(mPreviewHeight, mPreviewWidth);
            mMediaRecorder.setFilterType(mRenderer.mCurrentFilter);
            mMediaRecorder.start();
            isRecording = true;
        }
    }


    public void stopRecord() {
        if (isRecording) {
            if (mMediaRecorder != null) {
                mMediaRecorder.stop();
                mMediaRecorder = null;
            }
            isRecording = false;
        }
    }


    void setOnPreparedListener(OnPreviewListener preparedListener) {
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

    private boolean isSupported(List list, String target) {
        return list != null && list.contains(target);
    }

    private int mPreviewWidth = -1;
    private int mPreviewHeight = -1;

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

    public void startPlay() {
        if (!isPlaying) {
            try {
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
                    if (mSurfaceTexture != null) {
                        playWithSurfaceTexture(mSurfaceTexture);
                    }
                    this.mStartPlay = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            isPlaying = true;
        }
    }

    public void stopPlay() {
        this.mStartPlay = false;
        this.isPlaying = false;
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public void startPreview() {
        if (this.mCamera != null) {
            if (this.mOnErrorListener != null) {
                this.mOnErrorListener.onError(ERROR_CAMERA_EXISTED, "camera already initialized");
            }
            return;
        }
        if (this.mCameraId == CameraInfo.CAMERA_FACING_BACK) {
            this.mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
        } else {
            this.mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
        }
        if (mCamera == null) {
            if (this.mOnErrorListener != null) {
                this.mOnErrorListener.onError(ERROR_CAMERA_OPEN, "Unable to open camera");
            }
            return;
        }
        this.mCamera.setDisplayOrientation(90);
        if (this.mSurfaceTexture != null) {
            previewWithSurfaceTexture(this.mSurfaceTexture);
        }
        this.mStartPreview = true;
    }

    private SurfaceTexture mSurfaceTexture;

    private void handleSurfaceTexturePrepared(SurfaceTexture st) {
        if (mStartPreview) {
            this.mSurfaceTexture = st;
            previewWithSurfaceTexture(st);
        } else if (mStartPlay) {
            this.mSurfaceTexture = st;
            playWithSurfaceTexture(st);
        }
    }

    private void playWithSurfaceTexture(SurfaceTexture st) {
        try {
            Surface surface = new Surface(mSurfaceTexture);
            mMediaPlayer.setSurface(surface);
            st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    mGLSurfaceView.requestRender();
                }
            });
            final int videoWidth = mVideoObject.getVideoWidth();
            final int videoHeight = mVideoObject.getVideoHeight();
            this.mGLSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    mRenderer.setCameraPreviewSize(videoWidth, videoHeight);
                }
            });
            mMediaPlayer.setDataSource(mVideoObject.getVideoPath());
            mMediaPlayer.setLooping(true);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void previewWithSurfaceTexture(SurfaceTexture st) {
        try {
            this.mParameters = this.mCamera.getParameters();
            this.prepareCameraParameters();
            this.mCamera.setParameters(this.mParameters);
            this.mGLSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    mRenderer.setCameraPreviewSize(mPreviewWidth, mPreviewHeight);
                }
            });
            st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    mGLSurfaceView.requestRender();
                }
            });
            this.mCamera.setPreviewTexture(st);
            this.mCamera.startPreview();

            if (this.mOnPreparedListener != null) {
                this.mOnPreparedListener.onPreviewStarted(this.mPreviewWidth, this.mPreviewHeight);
            }

        } catch (IOException ioe) {
            if (this.mOnErrorListener != null) {
                this.mOnErrorListener.onError(ERROR_CAMERA_PREVIEW, "Unable to preview");
            }
        }
    }


    public void stopPreview() {
        this.mStartPreview = false;
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
    }

    public void release() {
        mSurfaceTexture = null;
        this.mGLSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.release();
            }
        });
    }

    public void changeFilterMode(final FilterType filterType) {
        mGLSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.changeFilterMode(filterType);
            }
        });
    }

    public interface OnErrorListener {
        void onError(int what, String msg);
    }

    public interface OnPreviewListener {
        void onPreviewStarted(int previewWidth, int previewHeight);
    }

    private static class VideoObject implements Serializable {
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


    private class CameraSurfaceRenderer implements GLSurfaceView.Renderer {

        private static final int RECORDING_OFF = 0;
        private static final int RECORDING_ON = 1;
        private static final int RECORDING_RESUMED = 2;
        private FullFrameRect mFullScreen;
        private final float[] mTexMatrix = new float[16];
        private int mTextureId;
        private SurfaceTexture mSurfaceTexture;
        private boolean mRecordingEnabled;
        private int mRecordingStatus;
        private boolean mIncomingSizeUpdated;
        private int mIncomingWidth;
        private int mIncomingHeight;
        private FilterType mCurrentFilter;
        private FilterType mNewFilter;
        private SurfaceHandler mSurfaceHandler;

        CameraSurfaceRenderer() {
            mSurfaceHandler = new SurfaceHandler(Looper.getMainLooper());
            mTextureId = -1;
            mRecordingStatus = -1;
            mRecordingEnabled = false;

            mIncomingSizeUpdated = false;
            mIncomingWidth = mIncomingHeight = -1;

            // We could preserve the old filter mode, but currently not bothering.
            mCurrentFilter = FILTER_NONE;
            mNewFilter = FilterType.FILTER_NORMAL;
        }

        private void changeFilterMode(FilterType filterType) {
            mNewFilter = filterType;
        }

        private FilterType getFilterType() {
            return mCurrentFilter;
        }

        public void release() {
            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }
            if (mFullScreen != null) {
                mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
                mFullScreen = null;             //  to be destroyed
            }
            mIncomingWidth = mIncomingHeight = -1;
        }

        public void changeRecordingState(boolean isRecording) {
            mRecordingEnabled = isRecording;
        }

        private void updateFilter() {
            FilterType.FilterInfo filterInfo = FilterType.getFilterInfo(mNewFilter);
            Texture2dProgram.ProgramType programType = filterInfo.programType;
            float[] kernel = filterInfo.kernel;
            float colorAdj = filterInfo.colorAdj;
            if (programType != mFullScreen.getProgram().getProgramType()) {
                mFullScreen.changeProgram(new Texture2dProgram(programType));
                mIncomingSizeUpdated = true;
            }
            if (kernel != null) {
                mFullScreen.getProgram().setKernel(kernel, colorAdj);
            }
            mCurrentFilter = mNewFilter;
        }

        void setCameraPreviewSize(int width, int height) {
            mIncomingWidth = width;
            mIncomingHeight = height;
            mIncomingSizeUpdated = true;
        }

        private boolean isPrepared = false;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            this.mRecordingStatus = RECORDING_OFF;
            this.mFullScreen = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
            this.mTextureId = this.mFullScreen.createTextureObject();
            this.mSurfaceTexture = new SurfaceTexture(mTextureId);
            this.isPrepared = true;
            this.mSurfaceHandler.sendMessage(mSurfaceHandler.obtainMessage(SurfaceHandler.MSG_SET_SURFACE_TEXTURE, this.mSurfaceTexture));
        }


        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }


        @Override
        public void onDrawFrame(GL10 gl) {
            mSurfaceTexture.updateTexImage();

            if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
                return;
            }

            if (isPlaying) {
                mNewFilter = FILTER_NORMAL;
            }

            if (mCurrentFilter != mNewFilter) {
                updateFilter();
            }

            if (mIncomingSizeUpdated) {
                mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
                mIncomingSizeUpdated = false;
            }

            mSurfaceTexture.getTransformMatrix(mTexMatrix);
            mFullScreen.drawFrame(mTextureId, mTexMatrix);

            if (isRecording) {
                if (mMediaRecorder != null) {
                    mMediaRecorder.onFrameAvailable(mTextureId, mSurfaceTexture);
                }
            }
        }
    }


    private class SurfaceHandler extends Handler {
        private static final int MSG_SET_SURFACE_TEXTURE = 0;

        SurfaceHandler(Looper mainLooper) {
            super(mainLooper);
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:
                    handleSurfaceTexturePrepared((SurfaceTexture) inputMessage.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }
}
