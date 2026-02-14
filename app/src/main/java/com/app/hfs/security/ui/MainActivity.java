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
 * FIXED BUILD ERRORS: 
 * 1. Removed reference to the deleted FaceSetupActivity.
 * 2. Maintained Dashboard re-selection crash fix.
 * 3. Maintained all Runtime Permissions (GPS, Dialer, Camera).
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
        // Uses NoActionBar parent theme to prevent crash
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
            // Prevents re-loading fragments when the active tab is clicked again.
            binding.bottomNav.setOnItemReselectedListener(item -> {
                // Do nothing to maintain stability
            });
        }

        // 4. Request all high-security permissions (GPS, Phone, Camera, SMS)
        checkAllSecurityPermissions();

        // 5. Setup Redirection
        // Removed FaceSetupActivity logic because HFS now uses System-Native Security.
        // Once the user sets their MPIN and number in settings, setup is considered done.
        if (getIntent().getBooleanExtra("SHOW_SETUP", false)) {
            Toast.makeText(this, "Welcome to HFS. Configure your MPIN in Settings.", Toast.LENGTH_LONG).show();
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
     */
    private void checkAllSecurityPermissions() {
        // List of mandatory runtime permissions
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
                Manifest.permission.ACCESS_FINE_LOCATION,
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

        // Check for System Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            showPermissionExplanation("Overlay Permission Required", 
                    "HFS needs 'Draw Over Other Apps' permission to block access to protected applications.",
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }

        // Check for Usage Stats Permission
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

    /**
     * UPDATED: Help dialog reflecting the System-Native security plan.
     */
    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("HFS Security Help")
                .setMessage("1. Ensure your phone has a screen lock (PIN, Fingerprint, or Face) enabled.\n\n" +
                           "2. In Settings, configure your HFS MPIN and Trusted Number for alerts.\n\n" +
                           "3. Select Gallery/Files in the 'Apps' tab to protect them.\n\n" +
                           "4. To open HFS if hidden: Dial your PIN and press Call.")
                .setPositiveButton("Got it", null)
                .show();
    }
}