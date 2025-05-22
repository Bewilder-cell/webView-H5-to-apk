package com.example.test;

import android.app.Application;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
           // 注册崩溃处理器
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(getApplicationContext()));
    }
} 
