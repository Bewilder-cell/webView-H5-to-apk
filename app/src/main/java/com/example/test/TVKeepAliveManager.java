package com.example.test;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class TVKeepAliveManager {
    private static final String TAG = "TVKeepAliveManager";
    
    // 海信电视相关
    private static final String HISENSE_PACKAGE = "com.hisense.tv";
    private static final String HISENSE_ACTION = "com.hisense.tv.action.KEEP_ALIVE";
    
    // 小米电视相关
    private static final String XIAOMI_PACKAGE = "com.mitv.tvhome";
    private static final String XIAOMI_ACTION = "com.mitv.tvhome.action.KEEP_ALIVE";
    
    public static void initKeepAlive(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        if (manufacturer.contains("hisense")) {
            initHisenseKeepAlive(context);
        } else if (manufacturer.contains("xiaomi")) {
            initXiaomiKeepAlive(context);
        }
    }
    
    private static void initHisenseKeepAlive(Context context) {
        try {
            // 检查海信电视系统
            PackageManager pm = context.getPackageManager();
            if (pm.getPackageInfo(HISENSE_PACKAGE, 0) != null) {
                // 发送保活广播
                Intent intent = new Intent(HISENSE_ACTION);
                intent.setPackage(HISENSE_PACKAGE);
                context.sendBroadcast(intent);
                
                // 设置自启动白名单
                Intent whiteListIntent = new Intent();
                whiteListIntent.setClassName(HISENSE_PACKAGE, "com.hisense.tv.settings.WhiteListActivity");
                whiteListIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(whiteListIntent);
                
                Log.d(TAG, "Hisense TV keep-alive initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Hisense keep-alive: " + e.getMessage());
        }
    }
    
    private static void initXiaomiKeepAlive(Context context) {
        try {
            // 检查小米电视系统
            PackageManager pm = context.getPackageManager();
            if (pm.getPackageInfo(XIAOMI_PACKAGE, 0) != null) {
                // 发送保活广播
                Intent intent = new Intent(XIAOMI_ACTION);
                intent.setPackage(XIAOMI_PACKAGE);
                context.sendBroadcast(intent);
                
                // 设置自启动白名单
                Intent whiteListIntent = new Intent();
                whiteListIntent.setClassName(XIAOMI_PACKAGE, "com.mitv.tvhome.settings.AutoStartActivity");
                whiteListIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(whiteListIntent);
                
                Log.d(TAG, "Xiaomi TV keep-alive initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Xiaomi keep-alive: " + e.getMessage());
        }
    }
} 
