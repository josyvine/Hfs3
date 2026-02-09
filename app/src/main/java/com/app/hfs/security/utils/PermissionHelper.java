package com.hfs.security.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.hfs.security.receivers.AdminReceiver;

/**
 * Utility class to verify system-level permissions required for HFS operations.
 * This class handles:
 * 1. Runtime Permissions (Camera, SMS).
 * 2. Special Access Permissions (Usage Stats, System Overlay).
 * 3. System Privileges (Device Admin).
 */
public class PermissionHelper {

    /**
     * Checks if the app has permission to use the Front Camera for intruder detection.
     */
    public static boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the app can send SMS alerts and receive remote commands.
     */
    public static boolean hasSmsPermissions(Context context) {
        boolean sendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED;
        boolean receiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) 
                == PackageManager.PERMISSION_GRANTED;
        
        return sendSms && receiveSms;
    }

    /**
     * Checks if 'Usage Access' is granted.
     * This is required for the AppMonitorService to detect when a protected app is opened.
     */
    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                Process.myUid(), context.getPackageName());
        
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Checks if 'Display Over Other Apps' is granted.
     * This is required to show the LockScreenActivity on top of protected apps.
     */
    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true; // Auto-granted on versions below Marshmallow
    }

    /**
     * Checks if the app is currently a Device Administrator.
     * Required for Phase 7 (Anti-Uninstall protection).
     */
    public static boolean isDeviceAdminActive(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(context, AdminReceiver.class);
        return dpm != null && dpm.isAdminActive(adminComponent);
    }

    /**
     * Checks for Notification permission (Required for Android 13+ / API 33+).
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Helper to determine if all essential security permissions are granted.
     * If this returns false, the app should prompt the user in MainActivity.
     */
    public static boolean isAllPermissionsGranted(Context context) {
        return hasCameraPermission(context) && 
               hasSmsPermissions(context) && 
               hasUsageStatsPermission(context) && 
               canDrawOverlays(context);
    }
}