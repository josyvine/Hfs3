package com.hfs.security.receivers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.hfs.security.R;
import com.hfs.security.ui.StealthUnlockActivity;
import com.hfs.security.utils.HFSDatabaseHelper;

/**
 * Advanced Stealth Mode Receiver.
 * FIXED: Implemented the 'Sticky Notification' plan to bypass Oppo background blocks.
 * 1. Detects PIN or *#PIN# USSD.
 * 2. Aborts call and shows a high-priority 'Verified' notification.
 * 3. Clicking notification opens the StealthUnlockActivity for Fingerprint access.
 */
public class StealthLaunchReceiver extends BroadcastReceiver {

    private static final String TAG = "HFS_StealthTrigger";
    private static final String STEALTH_CHANNEL_ID = "hfs_stealth_verified_channel";
    private static final int STEALTH_NOTIF_ID = 3003;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (action != null && action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            
            // 1. Get the number dialed
            String dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (dialedNumber == null) return;

            // 2. Get saved Custom PIN
            HFSDatabaseHelper db = HFSDatabaseHelper.getInstance(context);
            String savedPin = db.getMasterPin(); 

            if (savedPin == null || savedPin.isEmpty()) return;

            // 3. NORMALIZE FOR USSD SUPPORT
            // This captures: PIN, *#PIN#, and #PIN#
            String cleanDialed = dialedNumber.trim();
            String cleanSaved = savedPin.trim();

            boolean isMatch = cleanDialed.equals(cleanSaved) || 
                              cleanDialed.equals("*#" + cleanSaved + "#") || 
                              cleanDialed.equals("#" + cleanSaved + "#");

            if (isMatch) {
                Log.i(TAG, "Stealth Match confirmed for PIN: " + cleanSaved);

                // 4. ABORT CALL IMMEDIATELY
                setResultData(null);
                abortBroadcast();

                // 5. SHOW TOAST AS REQUESTED
                Toast.makeText(context, "HFS: Security PIN Verified. Check Notifications.", Toast.LENGTH_LONG).show();

                // 6. EXECUTE THE 'STICKY NOTIFICATION' PLAN
                showStickyVerifiedNotification(context);
            }
        }
    }

    /**
     * Creates a high-priority notification that Oppo cannot ignore.
     * Clicks lead to the StealthUnlockActivity (The Fingerprint/Unhide Popup).
     */
    private void showStickyVerifiedNotification(Context context) {
        // Intent to open the new Popup Activity
        Intent popupIntent = new Intent(context, StealthUnlockActivity.class);
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, popupIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    STEALTH_CHANNEL_ID, "HFS Identity Verification", 
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Build the 'Sticky' Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, STEALTH_CHANNEL_ID)
                .setSmallIcon(R.drawable.hfs)
                .setContentTitle("HFS Security")
                .setContentText("Identity Verified - Tap to open vault")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Vanishes once clicked
                .setOngoing(false);  // Not permanently stuck, but high priority

        if (notificationManager != null) {
            notificationManager.notify(STEALTH_NOTIF_ID, builder.build());
        }
    }
}