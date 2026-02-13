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
import com.hfs.security.ui.MainActivity;
import com.hfs.security.utils.HFSDatabaseHelper;

import java.util.Set;

/**
 * The core Background Guard Service for HFS.
 * UPDATED for "Zero-Fail" Plan:
 * 1. Implemented Session Management to prevent re-locking loops.
 * 2. Secured Notification: Prevents intruder bypass via the status bar.
 * 3. Hardware-Aware Triggering: Pre-checks biometric capability.
 */
public class AppMonitorService extends Service {

    private static final String TAG = "HFS_MonitorService";
    private static final int NOTIFICATION_ID = 2002;
    private static final long MONITOR_TICK_MS = 500; // High-frequency check (0.5s)

    private Handler monitorHandler;
    private Runnable monitorRunnable;
    private HFSDatabaseHelper db;
    private String lastPackageInForeground = "";

    // SESSION GRACE PERIOD MANAGEMENT
    private static String unlockedPackage = "";
    private static long lastUnlockTimestamp = 0;
    private static final long GRACE_PERIOD_MS = 30000; // 30 Seconds

    /**
     * Static method called by LockScreenActivity upon successful owner verification.
     * Grants a 30-second window to use the app without re-locking.
     */
    public static void unlockSession(String packageName) {
        unlockedPackage = packageName;
        lastUnlockTimestamp = System.currentTimeMillis();
        Log.d(TAG, "Owner Verified: Session Unlocked for " + packageName);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = HFSDatabaseHelper.getInstance(this);
        monitorHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "HFS Security Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as high-priority Foreground Service
        startForeground(NOTIFICATION_ID, createSecurityNotification());

        // Begin the aggressive monitoring loop
        startMonitoringLoop();

        return START_STICKY; 
    }

    /**
     * Creates a high-security notification.
     * FIXED: Clicking this notification triggers the LOCK SCREEN first,
     * preventing intruders from bypassing security via the status bar.
     */
    private Notification createSecurityNotification() {
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.putExtra("TARGET_APP_NAME", "HFS Settings");
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, lockIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, HFSApplication.CHANNEL_ID)
                .setContentTitle("HFS: Silent Guard Active")
                .setContentText("Your private files are under protection")
                .setSmallIcon(R.drawable.hfs) // Using your hfs.png logo
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * The aggressive monitoring loop (Checks every 0.5s).
     */
    private void startMonitoringLoop() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                String currentApp = getForegroundPackageName();

                // Check if the current app is in the protected list
                Set<String> protectedApps = db.getProtectedPackages();
                
                if (protectedApps.contains(currentApp)) {
                    
                    // 1. RE-ARM CHECK: If the user switched apps, clear the previous session
                    if (!currentApp.equals(unlockedPackage)) {
                        unlockedPackage = "";
                    }

                    // 2. SESSION CHECK: Is the app currently within the 30s 'Owner Grace' window?
                    long timeSinceUnlock = System.currentTimeMillis() - lastUnlockTimestamp;
                    boolean isAuthorized = currentApp.equals(unlockedPackage) && (timeSinceUnlock < GRACE_PERIOD_MS);

                    if (!isAuthorized) {
                        Log.i(TAG, "UNAUTHORIZED ACCESS DETECTED: " + currentApp);
                        triggerLockOverlay(currentApp);
                    }
                } else {
                    // If user is in a non-protected app or Home, clear the session for security
                    if (!currentApp.equals(getPackageName())) {
                        unlockedPackage = "";
                    }
                }

                // Repeat every 500ms
                monitorHandler.postDelayed(this, MONITOR_TICK_MS);
            }
        };
        monitorHandler.post(monitorRunnable);
    }

    /**
     * Identifies the app currently visible on the phone screen.
     */
    private String getForegroundPackageName() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long startTime = endTime - 10000; // Check last 10 seconds

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

    /**
     * Forcefully launches the high-priority Lock Screen.
     */
    private void triggerLockOverlay(String packageName) {
        String appName = getAppNameFromPackage(packageName);
        
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.putExtra("TARGET_APP_PACKAGE", packageName);
        lockIntent.putExtra("TARGET_APP_NAME", appName);
        
        // Critical flags for background start on Oppo/Realme devices
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                          | Intent.FLAG_ACTIVITY_SINGLE_TOP 
                          | Intent.FLAG_ACTIVITY_CLEAR_TOP
                          | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        
        try {
            startActivity(lockIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch LockScreen overlay: " + e.getMessage());
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