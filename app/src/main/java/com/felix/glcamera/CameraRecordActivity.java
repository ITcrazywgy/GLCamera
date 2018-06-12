package com.felix.glcamera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.felix.glcamera.util.CameraUtils;
import com.felix.glcamera.widgets.AutoFitGLSurfaceView;
import com.felix.glcamera.widgets.ProgressView;

import java.io.File;

public class CameraRecordActivity extends Activity implements View.OnClickListener, CameraRecorderHelper.OnPreviewListener, CameraRecorderHelper.OnErrorListener {


    private CheckBox mCameraSwitch;
    private CheckBox mRecordLed;
    private ImageButton mRecordCancel;
    private ImageButton mRecordConfirm;
    private TextView mRecordGestureHint;
    private AutoFitGLSurfaceView mGLView;
    private ProgressView mProgressView;
    private Spinner mSpinnerFilter;

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

        mSpinnerFilter = findViewById(R.id.record_camera_filter);
        initSpinner();
    }


    private void initSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.cameraFilterNames, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFilter.setAdapter(adapter);
        mSpinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Spinner spinner = (Spinner) parent;
                final int filterNum = spinner.getSelectedItemPosition();
                if (mRecorderHelper != null) {
                    switch (filterNum) {
                        case 0:
                            mRecorderHelper.changeFilterMode(FilterType.FILTER_NORMAL);
                            break;
                        case 1:
                            mRecorderHelper.changeFilterMode(FilterType.FILTER_BLACK_WHITE);
                            break;
                        case 2:
                            mRecorderHelper.changeFilterMode(FilterType.FILTER_BLUR);
                            break;
                        case 3:
                            mRecorderHelper.changeFilterMode(FilterType.FILTER_SHARPEN);
                            break;
                        case 4:
                            mRecorderHelper.changeFilterMode(FilterType.FILTER_EDGE_DETECT);
                            break;
                        case 5:
                            mRecorderHelper.changeFilterMode(FilterType.FILTER_EMBOSS);
                            break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
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
        setFullScreen(getWindow());
        if (mRecorderHelper == null) {
            initMediaRecorderHelper();
        } else {
            if (mRecorderHelper.isVideoExists()) {
                mRecorderHelper.startPlay();
            } else {
                mRecordLed.setChecked(false);
                mRecorderHelper.startPreview();
            }
        }
        mGLView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRecorderHelper.stopPreview();
        mRecorderHelper.stopRecord();
        mRecorderHelper.stopPlay();
        mRecorderHelper.release();
        mGLView.onPause();
    }

    private static final int RECORD_TIME_MAX = 10 * 1000;
    public final static int RECORD_TIME_MIN = 1000;
    private int mMaxDuration = RECORD_TIME_MAX;

    private void initMediaRecorderHelper() {
        mRecorderHelper = new CameraRecorderHelper();
        mRecorderHelper.setOnErrorListener(this);
        mRecorderHelper.setOnPreparedListener(this);
        mRecorderHelper.setPreviewDisplay(mGLView);
        mRecorderHelper.startPreview();
        mProgressView.setMaxDuration(mMaxDuration);

        mProgressView.setOnProgressListener(new ProgressView.OnProgressListener() {
            @Override
            public void onProgressStart() {
                startRecord();
            }

            @Override
            public void onProgressCancel() {
                mRecorderHelper.stopRecord();
                mRecorderHelper.deleteVideoObject();
            }

            @Override
            public void onProgressEnd(float progress, long duration) {
                mRecorderHelper.stopRecord();
                mRecorderHelper.stopPreview();
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
                mRecorderHelper.startPreview();
            } else {
                //显示提交按钮，同时播放录制视频
                mProgressView.reset();
                mRecorderHelper.startPlay();
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

    private void startRecord() {
        String key = String.valueOf(System.currentTimeMillis());
        mRecorderHelper.setOutputDirectory(new File(Environment.getExternalStorageDirectory(), "AV/" + key).getAbsolutePath(), key);
        mRecorderHelper.startRecord();
        mCameraSwitch.setVisibility(View.GONE);
        mRecordLed.setVisibility(View.GONE);
        mSpinnerFilter.setVisibility(View.GONE);
        mRecordGestureHint.setVisibility(View.INVISIBLE);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


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
                mRecorderHelper.stopPlay();
                mRecorderHelper.startPreview();
                mProgressView.reset();
                initSpinner();
                hideNextStep();
                break;
        }
    }

    @Override
    public void onPreviewStarted(int previewWidth, int previewHeight) {
        this.mCameraSwitch.setVisibility(View.VISIBLE);
        this.mRecordLed.setVisibility(View.VISIBLE);
        this.mSpinnerFilter.setVisibility(View.VISIBLE);

        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            mGLView.setAspectRatio(previewHeight, previewWidth);
        } else {
            mGLView.setAspectRatio(previewWidth, previewHeight);
        }
    }


    @Override
    public void onError(int what, String msg) {
        Toast.makeText(this, "录制视频失败", Toast.LENGTH_SHORT).show();
        mRecorderHelper.deleteVideoObject();
        mProgressView.reset();
        finish();
    }


}
