package com.example.test;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final int PICK_REQUEST = 10001;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;
    private static final int JOB_ID = 100;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> permissionsList = new ArrayList<>();
            permissionsList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionsList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    String postNotifications = (String) android.Manifest.permission.class
                            .getField("POST_NOTIFICATIONS").get(null);
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

        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
                mFilePathCallback = uploadFile;
                handle(uploadFile);
            }

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

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            request.addRequestHeader("cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("下载中...");
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype));
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            showMessage("下载中...");
            BroadcastReceiver onComplete = new BroadcastReceiver() {
                public void onReceive(Context ctxt, Intent intent) {
                    showMessage("下载完成");
                    unregisterReceiver(this);
                }
            };
            registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e("WebView", "页面加载失败: " + error.getDescription());
                restartApp(); // 页面加载错误，重启应用
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e("WebView", "WebView 渲染进程崩溃，是否被杀: " + detail.didCrash());
                restartApp(); // WebView 崩溃时重启应用
                return true;
            }
        });

        // 加载网页
        webView.loadUrl("http://192.168.1.5:8080/");

        // 启动前台服务 + JobScheduler
        startForegroundService();
    }

    private void restartApp() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getApplicationContext().startActivity(intent);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Deprecated
    public void showMessage(String _s) {
        Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_REQUEST) {
            if (data != null) {
                handleCallback(data.getData());
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
                mFilePathCallbackArray.onReceiveValue(uri != null ? new Uri[]{uri} : null);
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

    private void startForegroundService() {
        startService(new Intent(this, ForegroundService.class));
        startService(new Intent(this, LocalService.class));
        startService(new Intent(this, RemoteService.class));
        scheduleJob();
    }

    private void scheduleJob() {
        ComponentName serviceComponent = new ComponentName(this, JobSchedulerService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setRequiresCharging(true);
        builder.setMinimumLatency(3 * 60 * 1000);
        builder.setOverrideDeadline(10 * 60 * 1000);
        builder.setPersisted(true);
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            int resultCode = jobScheduler.schedule(builder.build());
            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.d("MainActivity", "Job scheduled successfully!");
            }
        }
    }
}
