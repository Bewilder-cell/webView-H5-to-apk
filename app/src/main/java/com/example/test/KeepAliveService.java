
 package com.example.test;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class KeepAliveService extends Service {
    private static final String TAG = "KeepAliveService";
    private Handler handler;
    private static final long CHECK_INTERVAL = 5 * 60 * 1000; // 5分钟检查一次

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        startKeepAliveCheck();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startKeepAliveCheck() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndRestartServices();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        }, CHECK_INTERVAL);
    }

    private void checkAndRestartServices() {
        try {
            // 检查并重启前台服务
            if (!isServiceRunning(ForegroundService.class)) {
                Intent serviceIntent = new Intent(this, ForegroundService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }

            // 检查并重启本地服务
            if (!isServiceRunning(LocalService.class)) {
                startService(new Intent(this, LocalService.class));
            }

            // 检查并重启远程服务
            if (!isServiceRunning(RemoteService.class)) {
                startService(new Intent(this, RemoteService.class));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking services", e);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        // 服务被杀死时，尝试重启
        Intent intent = new Intent(this, KeepAliveService.class);
        startService(intent);
    }
}
