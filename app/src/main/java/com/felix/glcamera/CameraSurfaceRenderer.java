package com.felix.glcamera;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.felix.glcamera.CameraRecorderHelper.CameraHandler;
import com.felix.glcamera.gles.FullFrameRect;
import com.felix.glcamera.gles.Texture2dProgram;

import java.io.File;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Felix on 2018/5/26 16:56
 */

public class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "CameraSurfaceRenderer";
    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;

    private CameraHandler mCameraHandler;
    private TextureMovieEncoder mVideoEncoder;
    private File mOutputFile;

    private FullFrameRect mFullScreen;

    private final float[] mTexMatrix = new float[16];
    private int mTextureId;


    private SurfaceTexture mSurfaceTexture;
    private boolean mRecordingEnabled;
    private int mRecordingStatus;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;

    private int mCurrentFilter;
    private int mNewFilter;


    public CameraSurfaceRenderer(CameraHandler cameraHandler, TextureMovieEncoder movieEncoder, File outputFile) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;
        mOutputFile = outputFile;

        mTextureId = -1;

        mRecordingStatus = -1;
        mRecordingEnabled = false;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;

        // We could preserve the old filter mode, but currently not bothering.
        mCurrentFilter = -1;
        mNewFilter = CameraRecordActivity.FILTER_NONE;
    }

    public void notifyPausing() {
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
        Log.d(TAG, "changeRecordingState: was " + mRecordingEnabled + " now " + isRecording);
        mRecordingEnabled = isRecording;
    }

    public void updateFilter() {
        Texture2dProgram.ProgramType programType;
        float[] kernel = null;
        float colorAdj = 0.0f;
        switch (mNewFilter) {
            case CameraRecordActivity.FILTER_NONE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            case CameraRecordActivity.FILTER_BLACK_WHITE:

                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW;
                break;
            case CameraRecordActivity.FILTER_BLUR:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        1f / 16f, 2f / 16f, 1f / 16f,
                        2f / 16f, 4f / 16f, 2f / 16f,
                        1f / 16f, 2f / 16f, 1f / 16f};
                break;
            case CameraRecordActivity.FILTER_SHARPEN:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f};
                break;
            case CameraRecordActivity.FILTER_EDGE_DETECT:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f};
                break;
            case CameraRecordActivity.FILTER_EMBOSS:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f};
                colorAdj = 0.5f;
                break;
            default:
                throw new RuntimeException("Unknown filter mode " + mNewFilter);
        }
        if (programType != mFullScreen.getProgram().getProgramType()) {
            mFullScreen.changeProgram(new Texture2dProgram(programType));
            mIncomingSizeUpdated = true;
        }
        if (kernel != null) {
            mFullScreen.getProgram().setKernel(kernel, colorAdj);
        }
        mCurrentFilter = mNewFilter;
    }

    public void setCameraPreviewSize(int width, int height) {
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mRecordingEnabled = mVideoEncoder.isRecording();
        if (mRecordingEnabled) {
            mRecordingStatus = RECORDING_RESUMED;
        } else {
            mRecordingStatus = RECORDING_OFF;
        }
        mFullScreen = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullScreen.createTextureObject();
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        mSurfaceTexture.updateTexImage();
        if (mRecordingEnabled) {
            switch (mRecordingStatus) {
                case RECORDING_OFF:
                    mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(mOutputFile, 640, 480, 1000000, EGL14.eglGetCurrentContext()));
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        } else {
            switch (mRecordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    mVideoEncoder.stopRecording();
                    mRecordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    // yay
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        }
        mVideoEncoder.setTextureId(mTextureId);
        mVideoEncoder.frameAvailable(mSurfaceTexture);
        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            return;
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
    }
}
