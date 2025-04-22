package com.example.test;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 设置全局崩溃处理器
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        
        // 启动主服务
        startService(new Intent(this, ForegroundService.class));
    }
} 