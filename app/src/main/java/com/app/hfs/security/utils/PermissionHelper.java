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

import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;

/**
 * Advanced Permission & Hardware Manager.
 * FIXED: Implemented a specific Hardware Check for Face Authentication 
 * to distinguish between Face and Fingerprint sensors.
 */
public class PermissionHelper {

    /**
     * Step 1: Specific Face Hardware Check.
     * Logic: Verifies if the phone has dedicated Face hardware (3D/IR).
     * This prevents the app from accidentally defaulting to Fingerprint 
     * when we specifically want a Face scan.
     */
    public static boolean hasSystemFaceHardware(Context context) {
        PackageManager pm = context.getPackageManager();
        
        // 1. Check for the official Android Face Feature
        // This feature string is the standard for Face hardware
        boolean hasFaceFeature = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasFaceFeature = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
        } else {
            // Android 9 (Oppo) fallback check for common face hardware strings
            hasFaceFeature = pm.hasSystemFeature("android.hardware.biometrics.face") ||
                             pm.hasSystemFeature("com.oppo.face.trust");
        }

        // 2. Cross-reference with BiometricManager
        BiometricManager bm = BiometricManager.from(context);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        // Match: System must have Face hardware AND be ready to use Strong Biometrics
        return hasFaceFeature && (canAuth == BiometricManager.BIOMETRIC_SUCCESS);
    }

    /**
     * Checks if the app can access GPS coordinates for tracking.
     */
    public static boolean hasLocationPermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if the app can intercept dialed numbers (Oppo Dialer Fix).
     */
    public static boolean hasPhonePermissions(Context context) {
        boolean statePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                == PackageManager.PERMISSION_GRANTED;
        
        boolean callPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.PROCESS_OUTGOING_CALLS) 
                == PackageManager.PERMISSION_GRANTED;

        return statePerm && callPerm;
    }

    /**
     * Checks if the app has permission to show the Lock Screen overlay.
     */
    public static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true; 
    }

    /**
     * Checks if the app can detect foreground app launches.
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

    public static boolean isAllSecurityGranted(Context context) {
        return hasBasePermissions(context) && 
               hasPhonePermissions(context) && 
               hasLocationPermissions(context) && 
               hasUsageStatsPermission(context) && 
               canDrawOverlays(context);
    }

    public static void openAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}