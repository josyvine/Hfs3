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
 * UPDATED:
 * 1. Fixed Dashboard re-selection crash using ReselectedListener.
 * 2. Added GPS Location permission requests for the Map link enhancement.
 * 3. Added Phone/Call permissions for the Stealth Dialer fix.
 * 4. Stabilized the navigation stack for Oppo/ColorOS compatibility.
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
        // Themes.xml already set to NoActionBar to prevent "Action bar supplied by decor" crash
        setSupportActionBar(binding.toolbar);

        // 3. Initialize Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Top-level destinations (No back button on these)
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_home, 
                    R.id.nav_protected_apps, 
                    R.id.nav_history)
                    .build();

            // Sync Toolbar and BottomNav with NavController
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
            
            // FIX FOR DASHBOARD CRASH:
            // This prevents fragment re-creation errors when tapping the current tab.
            binding.bottomNav.setOnItemReselectedListener(item -> {
                // Ignore re-selection to maintain stability
            });
        }

        // 4. Request all required Security & Enhancement permissions
        checkAndRequestAllPermissions();

        // 5. Handle Setup flow redirection
        if (getIntent().getBooleanExtra("SHOW_SETUP", false)) {
            if (!db.isSetupComplete()) {
                startActivity(new Intent(this, FaceSetupActivity.class));
            }
        }
    }

    /**
     * Creates the top-right menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Handles Settings and Help menu items.
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
     * Permission System: Checks for Camera, SMS, Dialer, and GPS.
     */
    private void checkAndRequestAllPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
                Manifest.permission.ACCESS_FINE_LOCATION, // Added for GPS Map link
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        // Handle Android 13+ (API 33) Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Add to request list
                String[] extended = new String[permissions.length + 1];
                System.arraycopy(permissions, 0, extended, 0, permissions.length);
                extended[permissions.length] = Manifest.permission.POST_NOTIFICATIONS;
                permissions = extended;
            }
        }

        boolean anyMissing = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                anyMissing = true;
                break;
            }
        }

        if (anyMissing) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

        // Check for Overlay (Special Permission)
        if (!Settings.canDrawOverlays(this)) {
            requestSpecialAccess("Overlay Permission", 
                    "Required to show the security lock screen over your apps.",
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }

        // Check for Usage Stats (Special Permission)
        if (!hasUsageStatsPermission()) {
            requestSpecialAccess("Usage Access", 
                    "Required to detect when you open protected apps like Gallery.",
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestSpecialAccess(String title, String message, Intent intent) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Enable", (dialog, which) -> startActivity(intent))
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("HFS Security Help")
                .setMessage("1. Use 'Rescan' in Settings to save your face.\n\n" +
                           "2. Select System Gallery/Files in 'Apps' tab.\n\n" +
                           "3. Dialer: Dial your PIN (e.g., 2080) or *#2080# and press CALL to open app if hidden.\n\n" +
                           "4. Alerts: HFS sends Location and Maps link to your trusted phone if an intruder is caught.")
                .setPositiveButton("Got it", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}