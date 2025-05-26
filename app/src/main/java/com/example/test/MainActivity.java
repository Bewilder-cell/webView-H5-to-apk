package com.example.test;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.view.ViewGroup;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Objects;

import com.example.test.ForegroundService;
import com.example.test.LocalService;
import com.example.test.RemoteService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private WebView webView;
    private final int PICK_REQUEST = 10001;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;
    private static final int JOB_ID = 100;

    private BroadcastReceiver restartReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.test.RESTART_APP".equals(intent.getAction())) {
                Intent restartIntent = new Intent(context, MainActivity.class);
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(restartIntent);
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 注册崩溃处理器
        // Thread.setDefaultUncaughtExceptionHandler(CrashHandler.getInstance(this));
        // 注册重启广播接收器
        registerRestartReceiver();
            // 添加10秒后重启的测试代码
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Test: Triggering app restart after 10 seconds...");
                    restartApp();
                }
            }, 30000); // 10秒后触发
        // 请求忽略电池优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }

        //隐藏ActionBar
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        
        // 启动所有保活服务
        startKeepAliveServices();
        
        //初始化WebView
        initWebView();
        
        // 延迟请求权限
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                requestPermissionsIfNeeded();
            }
        }, 5000); // 延迟5秒
    }

    private void initWebView() {
        try {
            webView = findViewById(R.id.web_view);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setAllowFileAccess(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }

            // 添加错误处理
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    Log.e("WebView", "Error: " + description);
                    restartApp();
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });

            // 设置文件选择器
            webView.setWebChromeClient(new WebChromeClient() {
                // Andorid 4.1----4.4
                public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
                    mFilePathCallback = uploadFile;
                    handle(uploadFile);
                }

                // for 5.0+
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                    if (mFilePathCallbackArray != null) {
                        mFilePathCallbackArray.onReceiveValue(null);
                    }
                    mFilePathCallbackArray = filePathCallback;
                    handleup(filePathCallback);
                    return true;
                }
                
                private void handle(ValueCallback<Uri> uploadFile) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("*/*");
                    startActivityForResult(intent, PICK_REQUEST);
                }

                private void handleup(ValueCallback<Uri[]> uploadFile) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("*/*");
                    startActivityForResult(intent, PICK_REQUEST);
                }
            });

            // 设置下载监听器
            webView.setDownloadListener(new DownloadListener() {
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    String cookies = CookieManager.getInstance().getCookie(url);
                    request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("下载中...");
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                    DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    manager.enqueue(request);
                    showMessage("下载中...");

                    //Notif if success
                    BroadcastReceiver onComplete = new BroadcastReceiver() {
                        public void onReceive(Context ctxt, Intent intent) {
                            showMessage("下载完成");
                            unregisterReceiver(this);
                        }
                    };
                    registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
            });

            // 获取当前时间戳
            long timestamp = System.currentTimeMillis();
            String url = "http://10.84.4.173:8081?t=" + timestamp;
            webView.loadUrl(url);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing WebView", e);
            restartApp();
        }
    }

    private void restartApp() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        SystemClock.elapsedRealtime() + 500, // 0.5秒后重启
                        pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        SystemClock.elapsedRealtime() + 500,
                        pendingIntent
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting app", e);
        }
    }

    private void registerRestartReceiver() {
        IntentFilter filter = new IntentFilter("com.example.test.RESTART_APP");
        registerReceiver(restartReceiver, filter);
    }

    private void startKeepAliveServices() {
        // 启动前台服务
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 启动双进程保活服务
        startService(new Intent(this, LocalService.class));
        startService(new Intent(this, RemoteService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(restartReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.clearCache(true);
                webView.clearHistory();
                webView.clearFormData();
                webView.clearSslPreferences();
                webView.removeAllViews();
                webView.loadUrl("about:blank");
                ((ViewGroup) webView.getParent()).removeView(webView);
                webView.destroy();
                webView = null;
            } catch (Exception e) {
                Log.e(TAG, "Error destroying WebView", e);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_REQUEST) {
            if (null != data) {
                Uri uri = data.getData();
                handleCallback(uri);
            } else {
                handleCallback(null);
            }
        } else {
            handleCallback(null);
        }
    }

    private void handleCallback(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mFilePathCallbackArray != null) {
                if (uri != null) {
                    mFilePathCallbackArray.onReceiveValue(new Uri[]{uri});
                } else {
                    mFilePathCallbackArray.onReceiveValue(null);
                }
                mFilePathCallbackArray = null;
            }
        } else {
            if (mFilePathCallback != null) {
                if (uri != null) {
                    String url = getFilePathFromContentUri(uri, getContentResolver());
                    Uri u = Uri.fromFile(new File(url));
                    mFilePathCallback.onReceiveValue(u);
                } else {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = null;
            }
        }
    }

    public static String getFilePathFromContentUri(Uri selectedVideoUri, ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};
        Cursor cursor = contentResolver.query(selectedVideoUri, filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    @Deprecated
    public void showMessage(String _s) {
        Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsList = new ArrayList<>();
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);

            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    String postNotifications = (String) Manifest.permission.class
                            .getField("POST_NOTIFICATIONS")
                            .get(null);
                    permissionsList.add(postNotifications);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            List<String> toRequest = new ArrayList<>();
            for (String permission : permissionsList) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    toRequest.add(permission);
                }
            }

            if (!toRequest.isEmpty()) {
                requestPermissions(toRequest.toArray(new String[0]), 1);
            }
        }
    }
}