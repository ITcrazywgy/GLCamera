package com.felix.glcamera.util;

import android.annotation.SuppressLint;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;


public class CameraUtils {
    private static final String TAG = "CameraUtils";

    public static Camera.Size chooseOptimalSize(List<Camera.Size> choices, int preferPreviewWidth, int preferPreviewHeight) {
        List<Camera.Size> bigEnough = new ArrayList<>();
        List<Camera.Size> notBigEnough = new ArrayList<>();
        for (Camera.Size option : choices) {
            if (option.width >= preferPreviewWidth && option.height >= preferPreviewHeight) {
                bigEnough.add(option);
            } else {
                notBigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices.get(0);
        }
    }

    private static class CompareSizesByArea implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return Long.signum((long) lhs.width * lhs.height - (long) rhs.width * rhs.height);
        }
    }

    private static boolean checkCameraFacing(final int facing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            return false;
        }
        final int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (facing == info.facing) {
                return true;
            }
        }
        return false;
    }

    @SuppressLint("ObsoleteSdkInt")
    public static boolean isSupportFront() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) return false;
        final int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == CAMERA_FACING_FRONT) {
                return true;
            }
        }
        return false;
    }


    public static boolean isSupportLedFlash(PackageManager packageManager) {
        FeatureInfo[] availableFeatures = packageManager.getSystemAvailableFeatures();
        if (availableFeatures != null) {
            for (FeatureInfo featureInfo : availableFeatures) {
                if (featureInfo != null && PackageManager.FEATURE_CAMERA_FLASH.equals(featureInfo.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isDevice(String... devices) {
        String model = Build.MODEL;
        if (devices != null && model != null) {
            for (String device : devices) {
                if (model.trim().contains(device)) {
                    return true;
                }
            }
        }
        return false;
    }
}
