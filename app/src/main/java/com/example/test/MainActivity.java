package com.example.test;

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
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Objects;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.Handler;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final int PICK_REQUEST = 10001;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;
    private static final int JOB_ID = 100;
    private final Handler handler = new Handler();
    private final int RELOAD_INTERVAL = 20 * 60 * 1000; // 每10分钟重载一次

    private final Runnable reloadRunnable = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
                String timestampUrl = getUrlWithTimestamp();
                webView.loadUrl(timestampUrl);
                Log.d("WebView", "重新加载页面：" + timestampUrl);
            }
            handler.postDelayed(this, RELOAD_INTERVAL);
        }
    };

    private String getUrlWithTimestamp() {
        long timestamp = System.currentTimeMillis();
        return "http://10.114.136.173:8082/#/?t=" + timestamp;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                view.loadUrl(getUrlWithTimestamp()); // 加载失败时重试
            }
        });

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

                BroadcastReceiver onComplete = new BroadcastReceiver() {
                    public void onReceive(Context ctxt, Intent intent) {
                        showMessage("下载完成");
                        unregisterReceiver(this);
                    }
                };

                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        });

        // WebView 设置优化
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 加载网页
        webView.loadUrl(getUrlWithTimestamp());

        // 启动前台服务和保活
        startForegroundService();

        // 开启定时刷新任务
        handler.postDelayed(reloadRunnable, RELOAD_INTERVAL);

        // Web 页中尝试触发音频加载
        webView.loadUrl("javascript:(function() { " +
            "var audio = document.getElementById('alarmSound');" +
            "if(audio){audio.load();}" +
            "})()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(reloadRunnable);
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
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        startService(serviceIntent);
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
