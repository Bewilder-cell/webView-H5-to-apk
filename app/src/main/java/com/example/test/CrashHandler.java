package com.example.test;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final String CRASH_LOG_DIR = "crash_logs";
    private static final long RESTART_COOLDOWN_MS = 30 * 60 * 1000; // 30分钟冷却时间
    private static final long RESTART_DELAY_MS = 3000; // 5秒后重启
    private static final int MAX_CRASHES_PER_DAY = 30; // 24小时内最大崩溃次数
    
    private static CrashHandler instance;
    private Context context;
    private long lastCrashTime = 0;
    private int crashCount = 0;
    private long lastCrashCountResetTime = 0;
    
    private CrashHandler(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized CrashHandler getInstance(Context context) {
        if (instance == null) {
            instance = new CrashHandler(context);
        }
        return instance;
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            // 记录崩溃日志
            saveCrashLog(ex);
            
            // 检查是否需要重启
            if (shouldRestart()) {
                // 延迟重启
                restartAppWithDelay();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling crash", e);
        } finally {
            // 确保应用退出
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }
    
    private boolean shouldRestart() {
        long currentTime = System.currentTimeMillis();
        
        // 检查是否需要重置崩溃计数
        if (currentTime - lastCrashCountResetTime > 24 * 60 * 60 * 1000) { // 24小时
            crashCount = 0;
            lastCrashCountResetTime = currentTime;
        }
        
        // 检查崩溃次数是否超过限制
        if (crashCount >= MAX_CRASHES_PER_DAY) {
            Log.w(TAG, "Too many crashes in 24 hours, not restarting");
            return false;
        }
        
        // 检查冷却时间
        if (currentTime - lastCrashTime < RESTART_COOLDOWN_MS) {
            Log.w(TAG, "Too soon since last crash, not restarting");
            return false;
        }
        
        // 更新崩溃时间和计数
        lastCrashTime = currentTime;
        crashCount++;
        
        return true;
    }
    
    private void restartAppWithDelay() {
        try {
            Log.i(TAG, "Scheduling app restart in " + RESTART_DELAY_MS + "ms");
            
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + RESTART_DELAY_MS,
                    pendingIntent
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling restart", e);
        }
    }
    
    private void saveCrashLog(Throwable ex) {
        try {
            // 创建崩溃日志目录
            File logDir = new File(context.getFilesDir(), CRASH_LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 生成日志文件名
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(new Date());
            File logFile = new File(logDir, "crash_" + timestamp + ".txt");
            
            // 写入崩溃信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            
            String crashInfo = String.format(
                "Time: %s\n" +
                "Thread: %s\n" +
                "Exception: %s\n" +
                "Message: %s\n" +
                "Stack trace:\n%s",
                new Date(),
                Thread.currentThread().getName(),
                ex.getClass().getName(),
                ex.getMessage(),
                sw.toString()
            );
            
            // 保存到文件
            FileOutputStream fos = new FileOutputStream(logFile);
            fos.write(crashInfo.getBytes());
            fos.close();
            
            Log.i(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving crash log", e);
        }
    }
}
