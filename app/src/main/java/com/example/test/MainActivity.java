package com.example.test;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final int PICK_REQUEST = 10001;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;
    private static final int JOB_ID = 100;
    
    // 修改内存监控相关变量
    private static final int MEMORY_THRESHOLD = 40; // 降低到40MB
    private static final int CRITICAL_MEMORY_THRESHOLD = 20; // 严重不足阈值20MB
    private Handler memoryCheckHandler;
    private static final long MEMORY_CHECK_INTERVAL = 5000; // 缩短到5秒检查一次
    private static final String TAG = "MainActivity";
    private static final long AUTO_RELOAD_INTERVAL = 3600000; // 1小时自动重载一次
    private int consecutiveLowMemoryCount = 0; // 连续低内存计数
    private static final int MAX_LOW_MEMORY_COUNT = 3; // 连续3次触发强制重载

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏ActionBar
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        
        setupWebView();
        startMemoryMonitoring();
    }

    private void setupWebView() {
        webView = findViewById(R.id.web_view);
        
        // 优化WebView设置
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        // 更激进的缓存策略
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAppCacheEnabled(false);
        settings.setDatabaseEnabled(false); // 禁用数据库
        settings.setGeolocationEnabled(false); // 禁用地理位置
        settings.setSaveFormData(false); // 禁用表单数据保存
        settings.setLoadsImagesAutomatically(true); // 允许自动加载图片
        
        // 设置低内存模式
        settings.setRenderPriority(WebSettings.RenderPriority.LOW);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        // 设置WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成后执行内存清理
                clearWebViewMemory();
            }
        });

        // 设置WebChromeClient
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
                // 设置允许上传的文件类型
                intent.setType("*/*");
                startActivityForResult(intent, PICK_REQUEST);
            }

            private void handleup(ValueCallback<Uri[]> uploadFile) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_REQUEST);
            }
        });

        // wevView监听 H5 页面的下载事件
        // code from https://github.com/madhan98/Android-webview-upload-download/blob/master/app/src/main/java/com/my/newproject/MainActivity.java by Madhan
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

        // 获取当前时间戳
        long timestamp = System.currentTimeMillis();

        // 这里填你需要打包的 H5 页面链接，并附加时间戳参数
        String url = "http://10.114.136.173:8082/#/pages/views/pickTemplate/pickStateTWSB?t=" + timestamp;

        // 这里填你需要打包的 H5 页面链接
        webView.loadUrl(url);

        //显示一些小图片（头像）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // 允许使用 localStorage sessionStorage
        webView.getSettings().setDomStorageEnabled(true);
        // 是否支持 html 的 meta 标签
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().getAllowUniversalAccessFromFileURLs();
        webView.getSettings().getAllowFileAccessFromFileURLs();
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        startForegroundService();

        webView.loadUrl("javascript:(function() { " +
            "var audio = document.getElementById('alarmSound');" +
            "audio.load();" +
            "})()");
    }

    //设置回退页面
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
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
            // 暂停时释放更多内存
            clearWebViewMemory();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onDestroy() {
        if (memoryCheckHandler != null) {
            memoryCheckHandler.removeCallbacksAndMessages(null);
        }
        
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
            webView.clearSslPreferences();
            webView.destroy();
            webView = null;
        }
        
        // 强制清理
        System.gc();
        Runtime.getRuntime().gc();
        
        super.onDestroy();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_REQUEST) {
            if (null != data) {
                Uri uri = data.getData();
                handleCallback(uri);
            } else {
                // 取消了照片选取的时候调用
                handleCallback(null);
            }
        } else {
            // 取消了照片选取的时候调用
            handleCallback(null);
        }
    }

    /**
     * 处理WebView的回调
     *
     * @param uri
     */
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
//      也可用下面的方法拿到cursor
//      Cursor cursor = this.context.managedQuery(selectedVideoUri, filePathColumn, null, null, null);

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    private void startForegroundService() {
        // 启动前台服务
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        startService(serviceIntent);
        
        // 启动双进程保活服务
        startService(new Intent(this, LocalService.class));
        startService(new Intent(this, RemoteService.class));
        
        // 设置并启动 JobScheduler
        scheduleJob();
    }
    
    private void scheduleJob() {
        ComponentName serviceComponent = new ComponentName(this, JobSchedulerService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
        
        // 设置任务在网络可用时执行
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        
        // 设置任务在设备充电时执行
        builder.setRequiresCharging(true);
        
        // 设置任务的最小延迟时间（3分钟）
        builder.setMinimumLatency(3 * 60 * 1000);
        
        // 设置任务的最大延迟时间（10分钟）
        builder.setOverrideDeadline(10 * 60 * 1000);
        
        // 设置在设备重启后是否继续执行
        builder.setPersisted(true);
        
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            int resultCode = jobScheduler.schedule(builder.build());
            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.d("MainActivity", "Job scheduled successfully!");
            }
        }
    }

    private void startMemoryMonitoring() {
        memoryCheckHandler = new Handler(Looper.getMainLooper());
        
        // 定期内存检查
        memoryCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkMemoryUsage();
                memoryCheckHandler.postDelayed(this, MEMORY_CHECK_INTERVAL);
            }
        }, MEMORY_CHECK_INTERVAL);
        
        // 定期重载页面
        memoryCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                reloadWebView();
                memoryCheckHandler.postDelayed(this, AUTO_RELOAD_INTERVAL);
            }
        }, AUTO_RELOAD_INTERVAL);
    }

    private void checkMemoryUsage() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        
        long availableMegs = memoryInfo.availMem / 1048576L; // 转换为MB
        
        Log.d(TAG, "当前可用内存: " + availableMegs + "MB");
        
        if (availableMegs < CRITICAL_MEMORY_THRESHOLD) {
            // 内存严重不足，强制重载
            Log.w(TAG, "内存严重不足，执行强制重载");
            reloadWebView();
            consecutiveLowMemoryCount = 0;
        } else if (availableMegs < MEMORY_THRESHOLD) {
            Log.w(TAG, "内存不足，执行清理");
            clearWebViewMemory();
            consecutiveLowMemoryCount++;
            
            if (consecutiveLowMemoryCount >= MAX_LOW_MEMORY_COUNT) {
                // 连续多次内存不足，执行重载
                reloadWebView();
                consecutiveLowMemoryCount = 0;
            }
        } else {
            consecutiveLowMemoryCount = 0;
        }
    }

    private void clearWebViewMemory() {
        if (webView != null) {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            webView.clearSslPreferences();
            
            // 清理DOM存储
            webView.getSettings().setDomStorageEnabled(false);
            webView.getSettings().setDomStorageEnabled(true);
            
            // 执行JavaScript垃圾回收
            webView.loadUrl("javascript:void(0)");
            
            // 触发垃圾回收
            System.gc();
            Runtime.getRuntime().gc();
        }
    }

    private void reloadWebView() {
        if (webView != null) {
            Log.d(TAG, "执行WebView重载");
            
            // 保存当前URL
            String currentUrl = webView.getUrl();
            
            // 清理所有资源
            clearWebViewMemory();
            
            // 重新加载页面
            if (currentUrl != null) {
                webView.loadUrl(currentUrl);
            }
            
            // 显示提示
            showMessage("页面已刷新");
        }
    }

}
