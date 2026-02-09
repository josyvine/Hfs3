package com.hfs.security.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * Device Administration Receiver (Phase 7).
 * This component provides HFS with system-level privileges to:
 * 1. Prevent unauthorized uninstallation.
 * 2. Support remote locking via SMS commands.
 * 3. Monitor for multiple failed device unlock attempts.
 */
public class AdminReceiver extends DeviceAdminReceiver {

    /**
     * Called when the user successfully grants 'Device Admin' permission 
     * in the system settings.
     */
    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "HFS: Anti-Tamper Protection Enabled", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when the user attempts to disable the 'Device Admin' permission.
     * This usually happens right before an uninstallation attempt.
     */
    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "HFS: Anti-Tamper Protection Disabled", Toast.LENGTH_SHORT).show();
    }

    /**
     * Triggered if the user changes their phone's lock screen password.
     */
    @Override
    public void onPasswordChanged(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordChanged(context, intent);
        // This can be logged for security auditing
    }

    /**
     * Triggered if someone fails to unlock the device multiple times.
     * This can be used as a secondary trigger for intruder capture.
     */
    @Override
    public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordFailed(context, intent);
        // Future implementation: Capture photo if phone unlock fails 3 times
    }

    /**
     * Required text to be shown when the user tries to deactivate 
     * this HFS security component.
     */
    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        return "Warning: Deactivating HFS Admin will disable Anti-Uninstall and Remote Lock features.";
    }
}