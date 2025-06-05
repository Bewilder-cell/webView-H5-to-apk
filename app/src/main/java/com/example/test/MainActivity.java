package com.example.test;
// 在 MainActivity.java 文件顶部添加这些导入
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Handler;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.INTERNET,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private final BroadcastReceiver restartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.test.RESTART_APP".equals(intent.getAction())) {
                Log.d(TAG, "Received restart broadcast");
                restartApp();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏ActionBar
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        setContentView(R.layout.activity_main);
        
        // 初始化 WebView
        initWebView();
        
        // 注册重启广播接收器
        registerReceiver(restartReceiver, new IntentFilter("com.example.test.RESTART_APP"));
        
        // 检查并请求权限
        checkAndRequestPermissions();
        
        // 添加10秒后重启的测试代码
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Test: Triggering app restart after 10 seconds...");
                restartApp();
            }
        }, 60000*60*5*24); // 25小时触发一次
    }

    private void initWebView() {
        webView = findViewById(R.id.web_view); 
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true); 
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setDefaultFontSize(16);
        settings.setMinimumFontSize(12);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded: " + url);
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView error: " + description);
                // restartApp();
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "Loading progress: " + newProgress + "%");
            }
        });
        
        // 设置下载监听器
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                Log.d(TAG, "Download started: " + url);
                // 处理下载
                handleDownload(url, contentDisposition, mimeType);
            }
        });
        
        // 加载URL
        String url = "http://192.168.6.212:8089";
        webView.loadUrl(url);
    }

    private void handleDownload(String url, String contentDisposition, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error handling download: " + e.getMessage());
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsToRequest.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Log.w(TAG, "Some permissions were not granted");
            }
        }
    }

    private void restartApp() {
        Log.d(TAG, "Restarting app...");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        
        // 使用更可靠的方式设置 PendingIntent
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            try {
                // 使用 setAlarmClock，这在电视系统上更可靠
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                        System.currentTimeMillis() + 100, pendingIntent), pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, 
                        System.currentTimeMillis() + 100, pendingIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting alarm: " + e.getMessage());
                // 备用方案：使用 Handler 延迟启动
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(intent);
                        finish();
                    }
                }, 100);
            }
        }
        
        // 发送广播作为备用重启方法
        sendBroadcast(new Intent("com.example.test.RESTART_APP"));
        
        // 结束当前进程
        finish();
        System.exit(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
        try {
            unregisterReceiver(restartReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
        }
    }
}