package com.felix.glcamera.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

public class ProgressView extends View {

    private Paint mArcPaint;
    private Paint mInnerCirclePaint;
    private Paint mOuterCirclePaint;

    private float mCurrentScaleFactor = SCALE_FACTOR_INIT;
    private static final float SCALE_FACTOR_MAX = 1.5f;
    private static final float SCALE_FACTOR_INIT = 1.0f;
    private static final int MAX_SCALE_DURATION = 150;
    private GestureDetector mGestureDetector;
    private long mCurrentDuration;


    public ProgressView(Context context) {
        super(context);
        init();
    }

    public ProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    private void init() {
        mArcPaint = new Paint();
        mArcPaint.setAntiAlias(true);
        mArcPaint.setStyle(Paint.Style.STROKE);
        mArcPaint.setStrokeCap(Paint.Cap.ROUND);
        mArcPaint.setColor(Color.parseColor("#FF24ff00"));

        mOuterCirclePaint = new Paint();
        mOuterCirclePaint.setAntiAlias(true);
        mOuterCirclePaint.setStyle(Paint.Style.FILL);
        mOuterCirclePaint.setColor(Color.parseColor("#FFB3A8A4"));

        mInnerCirclePaint = new Paint();
        mInnerCirclePaint.setAntiAlias(true);
        mInnerCirclePaint.setStyle(Paint.Style.FILL);
        mInnerCirclePaint.setColor(getResources().getColor(android.R.color.white));

        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
                startProgress();
            }
        });
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension((int) (width * mCurrentScaleFactor), (int) (height * mCurrentScaleFactor));
    }

    private ValueAnimator mScaleAnimator;

    private void animateScale(float end) {
        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
        }
        int duration = (int) ((Math.abs(end - mCurrentScaleFactor) / Math.abs(SCALE_FACTOR_MAX - SCALE_FACTOR_INIT)) * MAX_SCALE_DURATION);
        if (duration != 0) {
            mScaleAnimator = new ValueAnimator();
            mScaleAnimator.setInterpolator(new AccelerateInterpolator());
            mScaleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Object scale = animation.getAnimatedValue();
                    if (scale instanceof Float) {
                        mCurrentScaleFactor = (float) scale;
                        requestLayout();
                    }
                }
            });
            mScaleAnimator.setDuration(duration);
            mScaleAnimator.setFloatValues(mCurrentScaleFactor, end);
            mScaleAnimator.start();
        }
    }


    private boolean isLongPressStatus = false;
    private long mStartTime;


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
                cancelProgress();
                break;
            case MotionEvent.ACTION_UP:
                stopProgress();
                break;
        }
        return true;
    }

    private static final int MAX_DURATION_DEFAULT = 10 * 1000;
    private int mMaxDuration = MAX_DURATION_DEFAULT;

    public void setMaxDuration(int maxDuration) {
        this.mMaxDuration = maxDuration;
    }

    private OnProgressListener mOnProgressListener;

    public void setOnProgressListener(OnProgressListener listener) {
        this.mOnProgressListener = listener;
    }

    public interface OnProgressListener {
        void onProgressStart();

        void onProgressCancel();

        void onProgressEnd(float progress, long duration);
    }

    private RectF mArcRecF;

    private int dp2px(float dip) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dip * scale + 0.5f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int strokeWidth = dp2px(8f);
        mArcPaint.setStrokeWidth(strokeWidth);
        int halfStroke = strokeWidth / 2;
        RectF viewRect = new RectF(halfStroke, halfStroke, w - halfStroke, h - halfStroke);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        int size = Math.min(w, h);
        mArcRecF = new RectF(halfStroke, halfStroke, size - halfStroke, size - halfStroke);
        mArcRecF.offset(centerX - mArcRecF.centerX(), centerY - mArcRecF.centerY());
    }

    private void startProgress() {
        if (!isLongPressStatus) {
            isLongPressStatus = true;
            removeCallbacks(mStopRunnable);
            removeCallbacks(mCancelRunnable);
            removeCallbacks(mStartRunnable);
            post(mStartRunnable);
        }
    }

    public void reset() {
        isLongPressStatus = false;
        mStartTime = 0;
        mCurrentDuration = 0;
        mCurrentScaleFactor = SCALE_FACTOR_INIT;
        removeCallbacks(mStopRunnable);
        removeCallbacks(mCancelRunnable);
        removeCallbacks(mStartRunnable);
        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
        }
        requestLayout();
    }

    private void cancelProgress() {
        if (isLongPressStatus) {
            isLongPressStatus = false;
            removeCallbacks(mStopRunnable);
            removeCallbacks(mCancelRunnable);
            removeCallbacks(mStartRunnable);
            post(mCancelRunnable);
        }
    }

    private void stopProgress() {
        if (isLongPressStatus) {
            isLongPressStatus = false;
            removeCallbacks(mStopRunnable);
            removeCallbacks(mCancelRunnable);
            removeCallbacks(mStartRunnable);
            post(mStopRunnable);
        }
    }

    private static final String TAG = "ProgressView";

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int r = Math.min(width, height) / 2;
        int cx = width / 2;
        int cy = height / 2;
        //大圆
        canvas.drawCircle(cx, cy, r, mOuterCirclePaint);
        //小圆
        canvas.drawCircle(cx, cy, r * 0.7f * (2 - mCurrentScaleFactor), mInnerCirclePaint);
        Log.e(TAG, "onDraw:" + mCurrentScaleFactor);
        if (isLongPressStatus) {
            mCurrentDuration = System.currentTimeMillis() - mStartTime;
            if (mCurrentDuration >= mMaxDuration) {
                mCurrentDuration = mMaxDuration;
                stopProgress();
            }
        }
        canvas.drawArc(mArcRecF, -90, (float) mCurrentDuration / (float) mMaxDuration * 360, false, mArcPaint);
        if (isLongPressStatus) {
            invalidate();
        }
    }


    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            if (mOnProgressListener != null) {
                mOnProgressListener.onProgressStart();
            }
            mStartTime = System.currentTimeMillis();
            animateScale(SCALE_FACTOR_MAX);
            Log.e(TAG, "StartRunnable:" + mCurrentScaleFactor);
        }
    };

    private Runnable mCancelRunnable = new Runnable() {
        @Override
        public void run() {
            mStartTime = 0;
            mCurrentDuration = 0;
            if (mOnProgressListener != null) {
                mOnProgressListener.onProgressCancel();
            }
            animateScale(SCALE_FACTOR_INIT);
            Log.e(TAG, "CancelRunnable:" + mCurrentScaleFactor);
        }
    };


    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            mCurrentDuration = Math.min(System.currentTimeMillis() - mStartTime, mMaxDuration);
            if (mOnProgressListener != null) {
                mOnProgressListener.onProgressEnd((float) mCurrentDuration / (float) mMaxDuration, mCurrentDuration);
            }
            animateScale( SCALE_FACTOR_INIT);
        }
    };

}
