package com.felix.glcamera;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;

import com.felix.glcamera.TextureMovieEncoder.EncoderConfig;

import java.io.File;

/**
 * Created by Felix on 2018/6/10 00:01
 */

public class MediaRecorder implements IMediaRecorder {
    private TextureMovieEncoder mVideoEncoder;
    private GLSurfaceView mGLSurfaceView;
    private String mOutputFile;
    private int mBitRate;
    private int mVideoWidth;
    private int mVideoHeight;
    private FilterType mFilterType;

    public MediaRecorder(GLSurfaceView gLSurfaceView) {
        this.mGLSurfaceView = gLSurfaceView;
        mVideoEncoder = new TextureMovieEncoder();
    }

    @Override
    public void setOutputFile(String outputFile) {
        this.mOutputFile = outputFile;
    }

    public void setVideoEncodingBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    @Override
    public void setVideoSize(int width, int height) {
        this.mVideoWidth = width;
        this.mVideoHeight = height;
    }

    public void setFilterType(FilterType filterType) {
        this.mFilterType = filterType;
    }

    public FilterType getFilterType() {
        return mFilterType;
    }

    @Override
    public void start() {
        mGLSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                EGLContext eglContext = EGL14.eglGetCurrentContext();
                EncoderConfig encoderConfig = new EncoderConfig(new File(mOutputFile), mVideoWidth, mVideoHeight, mBitRate, eglContext, getFilterType());
                mVideoEncoder.startRecording(encoderConfig);
            }
        });
    }

    @Override
    public void stop() {
        mVideoEncoder.stopRecording();
        mVideoEncoder.waitForStop();
    }


    void onFrameAvailable(int mTextureId, SurfaceTexture mSurfaceTexture) {
        mVideoEncoder.setTextureId(mTextureId);
        mVideoEncoder.frameAvailable(mSurfaceTexture);
    }
}
