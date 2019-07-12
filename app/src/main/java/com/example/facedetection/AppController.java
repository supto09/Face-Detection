package com.example.facedetection;

import android.app.Application;


public class AppController extends Application {

    private static final String TAG = AppController.class.getSimpleName();


    public static AppController instance;

    public static AppController getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
