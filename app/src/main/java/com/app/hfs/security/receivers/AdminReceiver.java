package com.hfs.security.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.hfs.security.utils.LocationHelper;
import com.hfs.security.utils.SmsHelper;

/**
 * Device Administration Receiver.
 * FIXED: 
 * 1. Resolved build error by passing 4 arguments to SmsHelper.
 * 2. Optimized 'Lost Phone' tracking logic to trigger alert on system fingerprint fail.
 */
public class AdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "HFS_AdminReceiver";

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "HFS: System Protection Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "HFS: Warning - System Protection Disabled", Toast.LENGTH_SHORT).show();
    }

    /**
     * THE LOST PHONE TRIGGER:
     * Triggered by the Android OS when a screen unlock attempt fails.
     */
    @Override
    public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordFailed(context, intent);
        
        Log.e(TAG, "SECURITY BREACH: Device unlock failed. Detecting intruder location...");

        // 1. Check attempt count from System Policy Manager
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        int failedAttempts = dpm.getCurrentFailedPasswordAttempts();

        // 2. TRIGGER GPS & SMS ALERT FLOW
        // We call the LocationHelper to get coordinates and then pipe them to SmsHelper.
        LocationHelper.getDeviceLocation(context, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(String mapLink) {
                /*
                 * FIXED: Now passes 4 parameters to match the SmsHelper definition.
                 * required: Context, String, String, String
                 */
                SmsHelper.sendAlertSms(
                        context, 
                        "PHONE LOCK SCREEN", 
                        mapLink, 
                        "System Unlock Failure"
                );
            }

            @Override
            public void onLocationFailed(String error) {
                /*
                 * FIXED: Now passes 4 parameters to match the SmsHelper definition.
                 * required: Context, String, String, String
                 */
                SmsHelper.sendAlertSms(
                        context, 
                        "PHONE LOCK SCREEN", 
                        "GPS Location Unavailable", 
                        "System Unlock Failure"
                );
            }
        });

        Log.i(TAG, "Intruder Alert initiated. Attempt count: " + failedAttempts);
    }

    @Override
    public void onPasswordSucceeded(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Device unlocked by owner.");
    }

    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        return "CRITICAL: Disabling HFS Admin will stop the 'Lost Phone' GPS tracking and Alert system.";
    }
}