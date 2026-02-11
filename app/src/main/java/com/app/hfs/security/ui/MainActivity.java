package com.hfs.security.ui;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.hfs.security.R;
import com.hfs.security.databinding.ActivityMainBinding;
import com.hfs.security.utils.HFSDatabaseHelper;

/**
 * The Primary Host Activity for HFS Security.
 * UPDATED & FIXED: 
 * 1. Resolved Dashboard re-selection crash.
 * 2. Added Runtime Permissions for GPS Location (Map Link Enhancement).
 * 3. Added Runtime Permissions for Dialer/Call interception (Oppo Fix).
 * 4. Stabilized Navigation Controller and Toolbar integration.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private HFSDatabaseHelper db;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HFSDatabaseHelper.getInstance(this);

        // 2. Setup custom Toolbar
        // Parent theme in themes.xml is already set to NoActionBar to prevent crash
        setSupportActionBar(binding.toolbar);

        // 3. Initialize Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Define top-level destinations (Dashboard, Apps, Evidence)
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_home, 
                    R.id.nav_protected_apps, 
                    R.id.nav_history)
                    .build();

            // Link NavController to Toolbar and Bottom Navigation
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
            
            // FIX FOR DASHBOARD CRASH:
            // Prevents the Navigation component from trying to re-load the current 
            // fragment if the user taps the icon of the tab they are already viewing.
            binding.bottomNav.setOnItemReselectedListener(item -> {
                // Do nothing on re-selection to maintain fragment stability
            });
        }

        // 4. Check for and request all high-security permissions (including GPS and Phone)
        checkAllSecurityPermissions();

        // 5. Handle Setup redirection if necessary
        if (getIntent().getBooleanExtra("SHOW_SETUP", false)) {
            if (!db.isSetupComplete()) {
                startActivity(new Intent(this, FaceSetupActivity.class));
            }
        }
    }

    /**
     * Creates the options menu (3-dot menu).
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Handles menu item clicks (Settings, Help).
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            navController.navigate(R.id.nav_settings);
            return true;
        } else if (id == R.id.action_help) {
            showHelpDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handles back-button navigation within the fragment stack.
     */
    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }

    /**
     * Verifies the critical permissions required for HFS Security.
     * UPDATED: Now includes mandatory Dialer, Phone State, and GPS Location.
     */
    private void checkAllSecurityPermissions() {
        // List of mandatory runtime permissions
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
                Manifest.permission.ACCESS_FINE_LOCATION,   // Added for Location Enhancement
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        // Handle Android 13+ Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                
                String[] extended = new String[permissions.length + 1];
                System.arraycopy(permissions, 0, extended, 0, permissions.length);
                extended[permissions.length] = Manifest.permission.POST_NOTIFICATIONS;
                permissions = extended;
            }
        }

        // Check if any runtime permission is currently missing
        boolean needsRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needsRequest = true;
                break;
            }
        }

        // Request missing runtime permissions
        if (needsRequest) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

        // Check for System Overlay Permission (For the App Lock Screen)
        if (!Settings.canDrawOverlays(this)) {
            showPermissionExplanation("Overlay Permission Required", 
                    "HFS needs 'Draw Over Other Apps' permission to block access to protected applications.",
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }

        // Check for Usage Stats Permission (For App Launch Detection)
        if (!hasUsageStatsPermission()) {
            showPermissionExplanation("Usage Access Required", 
                    "HFS needs 'Usage Access' to monitor when private apps are being opened.",
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    /**
     * Checks if the app has been granted access to Usage Statistics.
     */
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Displays a dialog explaining why a special permission is needed.
     */
    private void showPermissionExplanation(String title, String message, Intent intent) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Grant Access", (dialog, which) -> startActivity(intent))
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("HFS Security Help")
                .setMessage("1. Use 'Rescan' in Settings to save your face identity.\n\n" +
                           "2. Select Gallery/Files in the 'Apps' tab. You can also protect HFS itself.\n\n" +
                           "3. Setup your Trusted Phone number for SMS & GPS alerts.\n\n" +
                           "4. To open HFS if hidden: Dial your PIN (e.g. 2080) or *#2080# and press Call.")
                .setPositiveButton("Got it", null)
                .show();
    }
}