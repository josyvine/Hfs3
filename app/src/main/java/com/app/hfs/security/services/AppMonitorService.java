package com.hfs.security.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hfs.security.HFSApplication;
import com.hfs.security.R;
import com.hfs.security.ui.LockScreenActivity;
import com.hfs.security.utils.HFSDatabaseHelper;

import java.util.Set;

/**
 * The core Background Service for HFS.
 * FIXED: Implemented 'Grace Period' logic to stop the infinite locking loop.
 * After a successful owner unlock, the service will ignore that specific app 
 * for 30 seconds to allow normal usage.
 */
public class AppMonitorService extends Service {

    private static final String TAG = "HFS_MonitorService";
    private static final int NOTIFICATION_ID = 2002;
    private static final long MONITOR_TICK_MS = 500; 

    private Handler monitorHandler;
    private Runnable monitorRunnable;
    private HFSDatabaseHelper db;
    private String lastPackageInForeground = "";

    // SESSION GRACE PERIOD VARIABLES
    private static String unlockedPackage = "";
    private static long lastUnlockTimestamp = 0;
    private static final long GRACE_PERIOD_MS = 30000; // 30 Seconds

    /**
     * Static method to be called by LockScreenActivity upon successful verify.
     * This tells the service to stop locking this app temporarily.
     */
    public static void unlockSession(String packageName) {
        unlockedPackage = packageName;
        lastUnlockTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = HFSDatabaseHelper.getInstance(this);
        monitorHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "Security Monitor Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start Foreground Service with secured notification
        startForeground(NOTIFICATION_ID, createSecurityNotification());

        // Start the aggressive monitoring loop
        startMonitoringLoop();

        return START_STICKY; 
    }

    private Notification createSecurityNotification() {
        // Secure the intent: Clicks now trigger the Lock Screen first
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.putExtra("TARGET_APP_NAME", "HFS Settings");
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, lockIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, HFSApplication.CHANNEL_ID)
                .setContentTitle("HFS Silent Guard Active")
                .setContentText("Your privacy is currently protected")
                .setSmallIcon(R.drawable.hfs)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startMonitoringLoop() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                String currentApp = getForegroundPackageName();

                if (!currentApp.equals(lastPackageInForeground)) {
                    lastPackageInForeground = currentApp;
                    
                    Set<String> protectedApps = db.getProtectedPackages();
                    
                    if (protectedApps.contains(currentApp)) {
                        
                        // FIX: CHECK IF THE SESSION IS CURRENTLY GRANTED
                        if (currentApp.equals(unlockedPackage)) {
                            long timePassed = System.currentTimeMillis() - lastUnlockTimestamp;
                            if (timePassed < GRACE_PERIOD_MS) {
                                // Within 30 seconds of owner unlock - Do NOT trigger loop
                                Log.d(TAG, "Grace Period Active for: " + currentApp);
                                monitorHandler.postDelayed(this, MONITOR_TICK_MS);
                                return;
                            } else {
                                // Session expired - Clear it
                                unlockedPackage = "";
                            }
                        }

                        Log.i(TAG, "TRIGGERING LOCK FOR: " + currentApp);
                        triggerLockOverlay(currentApp);
                    }
                }

                monitorHandler.postDelayed(this, MONITOR_TICK_MS);
            }
        };
        monitorHandler.post(monitorRunnable);
    }

    private String getForegroundPackageName() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long startTime = endTime - 1000 * 10;

        UsageEvents events = usm.queryEvents(startTime, endTime);
        UsageEvents.Event event = new UsageEvents.Event();
        String currentPkg = "";

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentPkg = event.getPackageName();
            }
        }
        return currentPkg;
    }

    private void triggerLockOverlay(String packageName) {
        String appName = getAppNameFromPackage(packageName);
        
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.putExtra("TARGET_APP_PACKAGE", packageName);
        lockIntent.putExtra("TARGET_APP_NAME", appName);
        
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                          | Intent.FLAG_ACTIVITY_SINGLE_TOP 
                          | Intent.FLAG_ACTIVITY_CLEAR_TOP
                          | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        
        try {
            startActivity(lockIntent);
        } catch (Exception e) {
            Log.e(TAG, "Overlay Trigger Failed: " + e.getMessage());
        }
    }

    private String getAppNameFromPackage(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return (String) pm.getApplicationLabel(ai);
        } catch (PackageManager.NameNotFoundException e) {
            return packageName; 
        }
    }

    @Override
    public void onDestroy() {
        if (monitorHandler != null && monitorRunnable != null) {
            monitorHandler.removeCallbacks(monitorRunnable);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}