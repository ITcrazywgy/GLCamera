package com.felix.glcamera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.felix.glcamera.util.CameraUtils;
import com.felix.glcamera.widgets.AutoFitGLSurfaceView;
import com.felix.glcamera.widgets.ProgressView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class CameraRecordActivity extends AppCompatActivity implements View.OnClickListener, CameraRecorderHelper.OnPreparedListener, CameraRecorderHelper.OnErrorListener {
    static final int FILTER_NONE = 0;
    static final int FILTER_BLACK_WHITE = 1;
    static final int FILTER_BLUR = 2;
    static final int FILTER_SHARPEN = 3;
    static final int FILTER_EDGE_DETECT = 4;
    static final int FILTER_EMBOSS = 5;

    private CheckBox mCameraSwitch;
    private CheckBox mRecordLed;
    private ImageButton mRecordCancel;
    private ImageButton mRecordConfirm;
    private TextView mRecordGestureHint;
    private AutoFitGLSurfaceView mGLView;
    private ProgressView mProgressView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreen(getWindow());
        setContentView(R.layout.activity_camera_record);
        initViews();
    }


    private void initViews() {
        mRecordGestureHint = findViewById(R.id.record_gesture_hint);
        mGLView = findViewById(R.id.record_preview);
        mCameraSwitch = findViewById(R.id.record_camera_switcher);
        mProgressView = findViewById(R.id.record_progress);
        mRecordLed = findViewById(R.id.record_camera_led);
        mRecordCancel = findViewById(R.id.record_cancel);
        mRecordConfirm = findViewById(R.id.record_ok);
        mRecordCancel.setOnClickListener(this);
        mRecordConfirm.setOnClickListener(this);

        findViewById(R.id.title_back).setOnClickListener(this);

        //是否支持前置摄像头
        if (CameraUtils.isSupportFront()) {
            mCameraSwitch.setOnClickListener(this);
        } else {
            mCameraSwitch.setEnabled(false);
        }
        //是否支持闪光灯
        if (CameraUtils.isSupportLedFlash(getPackageManager())) {
            mRecordLed.setOnClickListener(this);
        } else {
            mRecordLed.setEnabled(false);
        }

        mRecordGestureHint.postDelayed(new Runnable() {
            @Override
            public void run() {
                mRecordGestureHint.setVisibility(View.INVISIBLE);
            }
        }, 5000);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!isFinishing()) {
            setFullScreen(getWindow());
        }
    }

    protected void setFullScreen(Window window) {
        //隐藏虚拟按键，并且全屏
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            window.getDecorView().setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private CameraRecorderHelper mRecorderHelper;

    @Override
    protected void onResume() {
        super.onResume();
        if (mRecorderHelper == null) {
            initMediaRecorder();
        } else {
            if (mRecorderHelper.isVideoExists()) {
                mRecorderHelper.playVideo();
            } else {
                mRecordLed.setChecked(false);
                mRecorderHelper.prepare();
            }
        }
        mGLView.onResume();
    }

    private static final int RECORD_TIME_MAX = 10 * 1000;
    public final static int RECORD_TIME_MIN = 1000;
    private int mMaxDuration = RECORD_TIME_MAX;

    private void initMediaRecorder() {
        mRecorderHelper = new CameraRecorderHelper();
        mRecorderHelper.setOnErrorListener(this);
        mRecorderHelper.setOnPreparedListener(this);
        mRecorderHelper.setGLSurfaceView(mGLView);
        mRecorderHelper.prepare();
        mProgressView.setMaxDuration(mMaxDuration);
        mProgressView.setOnProgressListener(new ProgressView.OnProgressListener() {
            @Override
            public void onProgressStart() {
                startRecord();
            }

            @Override
            public void onProgressCancel() {
                stopRecord();
                mRecorderHelper.deleteVideoObject();
                mRecorderHelper.prepare();
            }

            @Override
            public void onProgressEnd(float progress, long duration) {
                stopRecord();
                checkStatus();
            }
        });
    }

    private void checkStatus() {
        if (!isFinishing()) {
            long duration = mRecorderHelper.getVideoDuration();
            if (duration < RECORD_TIME_MIN) {
                Toast.makeText(this, "录制时间太短,请重新录制", Toast.LENGTH_SHORT).show();
                mRecorderHelper.deleteVideoObject();
                mProgressView.reset();
                mRecorderHelper.prepare();
            } else {
                //显示提交按钮，同时播放录制视频
                mProgressView.reset();
                mRecorderHelper.playVideo();
                showNextStep();
            }
        }
    }

    private void showNextStep() {
        mProgressView.setVisibility(View.INVISIBLE);
        mRecordConfirm.setVisibility(View.VISIBLE);
        mRecordCancel.setVisibility(View.VISIBLE);
        mRecordConfirm.setTranslationX(mProgressView.getX() - mRecordConfirm.getX());
        mRecordConfirm
                .animate()
                .translationX(0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mRecordConfirm.setVisibility(View.VISIBLE);
                    }
                })
                .setInterpolator(new AccelerateInterpolator())
                .start();
        mRecordCancel.setTranslationX(mProgressView.getX() - mRecordCancel.getX());
        mRecordCancel
                .animate()
                .translationX(0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mRecordCancel.setVisibility(View.VISIBLE);
                    }
                })
                .setInterpolator(new AccelerateInterpolator())
                .start();
    }

    private void hideNextStep() {
        mProgressView.setVisibility(View.VISIBLE);
        mRecordCancel.setVisibility(View.INVISIBLE);
        mRecordConfirm.setVisibility(View.INVISIBLE);
    }

    private void stopRecord() {
        mRecorderHelper.stopRecord();
    }

    private void startRecord() {
        String key = String.valueOf(System.currentTimeMillis());
        mRecorderHelper.setOutputDirectory(new File(Environment.getExternalStorageDirectory(), key).getAbsolutePath(), key);
        mRecorderHelper.startRecord();
        mCameraSwitch.setVisibility(View.GONE);
        mRecordLed.setVisibility(View.GONE);
        mRecordGestureHint.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecord();
        mRecorderHelper.releasePlayer();
        mGLView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }



   /* public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        mRecordingEnabled = !mRecordingEnabled;
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(mRecordingEnabled);
            }
        });
    }*/

   /* private void updateControls() {
        Button toggleRelease = (Button) findViewById(R.id.toggleRecording_button);
        int id = mRecordingEnabled ? R.string.toggleRecordingOff : R.string.toggleRecordingOn;
        toggleRelease.setText(id);
    }*/


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.title_back:
                onBackPressed();
                break;
            case R.id.record_camera_switcher:// 前后摄像头切换
                if (mRecordLed.isChecked()) {
                    mRecorderHelper.toggleFlashMode();
                    mRecordLed.setChecked(false);
                }
                mRecorderHelper.switchCamera();
                if (mRecorderHelper.isFrontCamera()) {
                    mRecordLed.setEnabled(false);
                } else {
                    mRecordLed.setEnabled(true);
                }
                break;
            case R.id.record_camera_led://闪光灯
                if (!mRecorderHelper.isFrontCamera()) {
                    mRecorderHelper.toggleFlashMode();
                }
                break;
            case R.id.record_ok:
                //sendVideo();
                break;
            case R.id.record_cancel:
                mRecorderHelper.deleteVideoObject();
                mRecorderHelper.releasePlayer();
                mRecorderHelper.prepare();
                mProgressView.reset();
                hideNextStep();
                break;
        }
    }

    @Override
    public void onPrepared() {
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        int previewHeight = mRecorderHelper.getPreviewHeight();
        int previewWidth = mRecorderHelper.getPreviewWidth();
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            mGLView.setAspectRatio(previewHeight, previewWidth);
        } else {
            mGLView.setAspectRatio(previewWidth, previewHeight);
        }
    }

    @Override
    public void onVideoError(int what, int extra) {

    }


}
