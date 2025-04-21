public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final int PICK_REQUEST = 10001;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;
    private static final int JOB_ID = 100;
    private static final long RELOAD_INTERVAL_MS = 4 * 60 * 60 * 1000; // 每 4 小时

    private Handler handler = new Handler();

    private Runnable recreateWebViewRunnable = new Runnable() {
        @Override
        public void run() {
            recreateWebView();
            handler.postDelayed(this, RELOAD_INTERVAL_MS); // 循环执行
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);

        createWebView(); // 初次创建 WebView
        startForegroundService();

        // 启动定时重建 WebView
        handler.postDelayed(recreateWebViewRunnable, RELOAD_INTERVAL_MS);
    }

    private void createWebView() {
        webView = findViewById(R.id.web_view);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
                mFilePathCallback = uploadFile;
                openFilePicker();
            }

            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallbackArray != null) {
                    mFilePathCallbackArray.onReceiveValue(null);
                }
                mFilePathCallbackArray = filePathCallback;
                openFilePicker();
                return true;
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
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
            showMessage("下载中...");
        });

        long timestamp = System.currentTimeMillis();
        String url = "http://10.30.101.11:8282/#/?t=" + timestamp;
        webView.loadUrl(url);
    }

    private void recreateWebView() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        // 延迟一点点再重新创建，避免立即重建导致黑屏
        handler.postDelayed(() -> {
            setContentView(R.layout.activity_main); // 重新 setContentView，重新加载布局
            createWebView();
        }, 1000);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_REQUEST);
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
            jobScheduler.schedule(builder.build());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_REQUEST) {
            Uri uri = (data != null) ? data.getData() : null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mFilePathCallbackArray != null) {
                    mFilePathCallbackArray.onReceiveValue(uri != null ? new Uri[]{uri} : null);
                    mFilePathCallbackArray = null;
                }
            } else {
                if (mFilePathCallback != null) {
                    if (uri != null) {
                        String path = getFilePathFromContentUri(uri, getContentResolver());
                        uri = Uri.fromFile(new File(path));
                    }
                    mFilePathCallback.onReceiveValue(uri);
                    mFilePathCallback = null;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public static String getFilePathFromContentUri(Uri uri, ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};
        Cursor cursor = contentResolver.query(uri, filePathColumn, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            filePath = cursor.getString(columnIndex);
            cursor.close();
            return filePath;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(recreateWebViewRunnable);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    public void showMessage(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
