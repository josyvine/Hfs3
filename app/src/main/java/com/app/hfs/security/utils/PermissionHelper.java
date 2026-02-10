package com.hfs.security.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/**
 * Advanced Permission Manager for HFS Security.
 * FIXED: 
 * 1. Added specific checks for Outgoing Call interception (Dialer Fix).
 * 2. Added System Overlay verification (Lock Screen Fix).
 * 3. Optimized for Oppo/Realme ColorOS background restrictions.
 */
public class PermissionHelper {

    /**
     * Checks if the app can intercept and analyze the numbers you dial.
     * Required for the Stealth Mode Dialer to function on Oppo.
     */
    public static boolean hasPhonePermissions(Context context) {
        boolean statePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                == PackageManager.PERMISSION_GRANTED;
        
        boolean callPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.PROCESS_OUTGOING_CALLS) 
                == PackageManager.PERMISSION_GRANTED;

        return statePerm && callPerm;
    }

    /**
     * Checks if the app has permission to show the Lock Screen on top of other apps.
     * On Oppo, this is often called "Floating Windows" or "Display Pop-up Window".
     */
    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true; // Auto-granted on very old Android versions
    }

    /**
     * Checks if the app can detect when you open protected apps like Gallery.
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
     * Checks for standard Runtime Permissions (Camera and SMS).
     */
    public static boolean hasBasePermissions(Context context) {
        boolean camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
        
        boolean sendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED;

        return camera && sendSms;
    }

    /**
     * Master check to see if HFS is fully authorized to protect the phone.
     */
    public static boolean isAllSecurityGranted(Context context) {
        return hasBasePermissions(context) && 
               hasPhonePermissions(context) && 
               hasUsageStatsPermission(context) && 
               canDrawOverlays(context);
    }

    /**
     * Helper to open the specific Oppo/ColorOS "App Info" page 
     * where the user must manually enable "Auto-startup".
     */
    public static void openAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}