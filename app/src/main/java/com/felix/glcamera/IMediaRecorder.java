package com.felix.glcamera;

/**
 * Created by Felix on 2018/6/9 23:59
 */

public interface IMediaRecorder {
    void setOutputFile(String outputFile);

    void setVideoSize(int width, int height);

    void start();


    void stop();
}
