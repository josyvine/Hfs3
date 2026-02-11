package com.hfs.security.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hfs.security.R;
import com.hfs.security.databinding.ActivityLockScreenBinding;
import com.hfs.security.services.AppMonitorService;
import com.hfs.security.utils.FaceAuthHelper;
import com.hfs.security.utils.FileSecureHelper;
import com.hfs.security.utils.HFSDatabaseHelper;
import com.hfs.security.utils.LocationHelper;
import com.hfs.security.utils.SmsHelper;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Security Overlay Activity (Lock Screen).
 * FIXED: 
 * 1. Java Error: Resolved 'effectively final' variable error in GPS alert logic.
 * 2. Biometric Accuracy: Rejects intruders based on strict landmark proportions.
 * 3. GPS Integration: Fetches Google Maps link for intruder alerts.
 * 4. Loop Killer: Calls AppMonitorService to stop infinite locking via Session Grace.
 * 5. Fingerprint Fail Alert: Detects wrong finger on sensor and alerts owner.
 */
public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "HFS_LockScreen";
    private ActivityLockScreenBinding binding;
    private ExecutorService cameraExecutor;
    private FaceAuthHelper faceAuthHelper;
    private HFSDatabaseHelper db;
    private String targetPackage;
    
    private boolean isActionTaken = false;
    private boolean isProcessing = false;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());

    private Executor biometricExecutor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Security Window Flags for Oppo/Realme Overlay priority
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        binding = ActivityLockScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HFSDatabaseHelper.getInstance(this);
        faceAuthHelper = new FaceAuthHelper(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        targetPackage = getIntent().getStringExtra("TARGET_APP_PACKAGE");

        binding.lockContainer.setVisibility(View.GONE);
        binding.scanningIndicator.setVisibility(View.VISIBLE);

        setupBiometricAuth();
        startInvisibleCamera();

        // 2-Second Verification Watchdog - Forces lock if identity is not verified quickly
        watchdogHandler.postDelayed(() -> {
            if (!isActionTaken && !isFinishing()) {
                showDiagnosticError("java.lang.SecurityException: Identity verification timeout. Landmarks not detected.");
                triggerIntruderAlert(null);
            }
        }, 2000);

        binding.btnUnlockPin.setOnClickListener(v -> checkPinAndUnlock());
        binding.btnFingerprint.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
    }

    private void setupBiometricAuth() {
        biometricExecutor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, biometricExecutor, 
                new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // SUCCESS: Grant access and kill the locking loop
                onSecurityVerified();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // SENSOR MISMATCH: Potential thief attempt - Trigger alert immediately
                Log.e(TAG, "Biometric failure on system sensor. Alerting Owner.");
                triggerIntruderAlert(null);
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("HFS Identity Check")
                .setSubtitle("Confirm identity to access your app")
                .setNegativeButtonText("Use PIN")
                .build();
    }

    private void onSecurityVerified() {
        watchdogHandler.removeCallbacksAndMessages(null);
        if (targetPackage != null) {
            // STOP THE LOOP: Notify service that this package is granted 30s grace
            AppMonitorService.unlockSession(targetPackage);
        }
        finish();
    }

    private void showDiagnosticError(String errorDetail) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this, R.style.Theme_HFS_Dialog)
                .setTitle("Identity Mismatch Details")
                .setMessage(errorDetail)
                .setCancelable(false)
                .setPositiveButton("CLOSE", (dialog, which) -> dialog.dismiss())
                .show();
        });
    }

    private void startInvisibleCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.invisiblePreview.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processCameraFrame);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                triggerIntruderAlert(null);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processCameraFrame(@NonNull ImageProxy imageProxy) {
        if (isProcessing || isActionTaken) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        faceAuthHelper.authenticate(imageProxy, new FaceAuthHelper.AuthCallback() {
            @Override
            public void onMatchFound() {
                runOnUiThread(() -> onSecurityVerified());
            }

            @Override
            public void onMismatchFound() {
                // Known intruder - Show error details and lock
                String diagnostic = faceAuthHelper.getLastDiagnosticInfo();
                showDiagnosticError(diagnostic);
                triggerIntruderAlert(imageProxy);
            }

            @Override
            public void onError(String error) {
                isProcessing = false;
                imageProxy.close();
            }
        });
    }

    private void triggerIntruderAlert(ImageProxy imageProxy) {
        if (isActionTaken) return;
        isActionTaken = true;
        watchdogHandler.removeCallbacksAndMessages(null);

        runOnUiThread(() -> {
            binding.scanningIndicator.setVisibility(View.GONE);
            binding.lockContainer.setVisibility(View.VISIBLE);

            // Secretly save intruder photo locally
            if (imageProxy != null) {
                FileSecureHelper.saveIntruderCapture(LockScreenActivity.this, imageProxy);
            }

            // Fetch Location and Send External Alert (+91)
            getGPSAndSendAlert();
            
            // Re-trigger biometric prompt for the real owner
            biometricPrompt.authenticate(promptInfo);
        });
    }

    /**
     * FIX: Uses effectively final string to resolve build error.
     */
    private void getGPSAndSendAlert() {
        String rawAppName = getIntent().getStringExtra("TARGET_APP_NAME");
        if (rawAppName == null) rawAppName = "Protected Files";
        
        // This 'final' string solves the "referenced from an inner class" error
        final String finalAppName = rawAppName;

        LocationHelper.getDeviceLocation(this, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(String mapLink) {
                // Sends alert with Google Maps coordinates and 3-msg cooldown limit
                SmsHelper.sendAlertSms(LockScreenActivity.this, finalAppName, mapLink);
            }

            @Override
            public void onLocationFailed(String error) {
                SmsHelper.sendAlertSms(LockScreenActivity.this, finalAppName, "GPS Signal Unavailable");
            }
        });
    }

    private void checkPinAndUnlock() {
        String input = binding.etPinInput.getText().toString();
        if (input.equals(db.getMasterPin())) {
            onSecurityVerified();
        } else {
            binding.tvErrorMsg.setText("Incorrect Master PIN");
            binding.etPinInput.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        watchdogHandler.removeCallbacksAndMessages(null);
        cameraExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {}
}