package com.hfs.security.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.hfs.security.ui.SplashActivity;
import com.hfs.security.utils.HFSDatabaseHelper;

/**
 * Advanced Stealth Mode Trigger.
 * FIXED: Specifically optimized for Oppo/Realme ColorOS.
 * This receiver intercepts the outgoing call event, displays a verification 
 * toast as requested, and launches the app if the PIN matches.
 */
public class StealthLaunchReceiver extends BroadcastReceiver {

    private static final String TAG = "HFS_StealthReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // We listen for the 'New Outgoing Call' system broadcast
        String action = intent.getAction();
        
        if (action != null && action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            
            // 1. Retrieve the number exactly as typed by the user
            String dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            
            if (dialedNumber == null) {
                return;
            }

            // 2. Fetch the CUSTOMIZABLE PIN from the app's database
            HFSDatabaseHelper db = HFSDatabaseHelper.getInstance(context);
            String savedSecretPin = db.getMasterPin(); 

            // 3. Normalize strings (Remove any non-digit characters like *, #, or spaces)
            String cleanDialed = dialedNumber.replaceAll("[^\\d]", "");
            String cleanSaved = savedSecretPin.replaceAll("[^\\d]", "");

            // 4. Verification Logic
            if (!cleanSaved.isEmpty() && cleanDialed.equals(cleanSaved)) {
                
                Log.i(TAG, "Security PIN Match Detected: " + cleanDialed);

                // 5. USER REQUEST: Show Toast message immediately
                Toast.makeText(context, "HFS: Security PIN Verified. Opening...", Toast.LENGTH_LONG).show();

                /* 
                 * 6. ABORT THE CALL 
                 * We set the result data to null to tell the system 
                 * that this call should not proceed to the cellular network.
                 */
                setResultData(null);
                abortBroadcast();

                // 7. LAUNCH THE APP WITH HIGH-PRIORITY FLAGS
                Intent launchIntent = new Intent(context, SplashActivity.class);
                
                /*
                 * FLAG_ACTIVITY_NEW_TASK: 
                 * Mandatory for starting an Activity from a BroadcastReceiver.
                 * 
                 * FLAG_ACTIVITY_CLEAR_TOP & FLAG_ACTIVITY_SINGLE_TOP:
                 * Ensures that the app opens fresh and doesn't get stuck behind 
                 * other windows, which is common on Oppo devices.
                 */
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP 
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                
                try {
                    // Execute the background launch task
                    context.startActivity(launchIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Critical: Failed to force-open HFS: " + e.getMessage());
                }
            }
        }
    }
}