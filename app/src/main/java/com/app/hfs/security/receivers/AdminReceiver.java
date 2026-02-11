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
 * FIXED: Lost Phone Monitoring.
 * This component handles system-level security breaches. 
 * If a thief or intruder fails the phone's main lock screen (PIN, Pattern, or Fingerprint),
 * this receiver triggers the GPS location capture and sends a high-priority alert.
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
     * FIXED: Now immediately initiates GPS tracking and SMS alerts.
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
                // Send alert with Google Maps link to the second phone
                SmsHelper.sendAlertSms(context, "PHONE LOCK SCREEN (Lost Phone Mode)", mapLink);
            }

            @Override
            public void onLocationFailed(String error) {
                // If GPS is disabled or blocked, send the alert without the link
                SmsHelper.sendAlertSms(context, "PHONE LOCK SCREEN (Lost Phone Mode)", "GPS Location Unavailable");
            }
        });

        Log.i(TAG, "Intruder Alert sent for failed attempt #" + failedAttempts);
    }

    @Override
    public void onPasswordSucceeded(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Device unlocked by owner.");
    }

    /**
     * Message shown to the user when they try to deactivate this security component.
     */
    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        return "CRITICAL: Disabling HFS Admin will stop the 'Lost Phone' GPS tracking and Alert system.";
    }
}