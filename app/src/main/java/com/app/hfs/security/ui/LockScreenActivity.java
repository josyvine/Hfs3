package com.hfs.security.ui;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
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
 * The Security Overlay Activity.
 * FIXED: 
 * 1. Resolved Android 9 (API 28) Authenticator combination crash.
 * 2. Implemented KeyguardManager fallback for System PIN on older devices.
 * 3. Maintained Invisible Intruder Capture and HFS MPIN backup.
 */
public class LockScreenActivity extends AppCompatActivity {

    private static final String TAG = "HFS_LockScreen";
    private static final int SYSTEM_CREDENTIAL_REQUEST_CODE = 505;

    private ActivityLockScreenBinding binding;
    private ExecutorService cameraExecutor;
    private HFSDatabaseHelper db;
    private String targetPackage;
    
    private boolean isActionTaken = false;
    private boolean isCameraCaptured = false;
    private ImageProxy lastCapturedFrame = null;

    private Executor biometricExecutor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent loop re-triggering while this screen is active
        AppMonitorService.isLockActive = true;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        binding = ActivityLockScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = HFSDatabaseHelper.getInstance(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        targetPackage = getIntent().getStringExtra("TARGET_APP_PACKAGE");

        binding.lockContainer.setVisibility(View.VISIBLE);

        // 1. Setup Camera for silent intruder capture
        startInvisibleCamera();

        // 2. Setup Security based on Android Version (Fixes Oppo Android 9 Crash)
        setupSystemSecurity();

        // 3. Trigger initial authentication
        triggerSystemAuth();

        // UI Listeners
        binding.btnUnlockPin.setOnClickListener(v -> checkMpinAndUnlock());
        binding.btnFingerprint.setOnClickListener(v -> triggerSystemAuth());
    }

    /**
     * Logic: Splits the logic between Android 10+ and Android 9 (Your device).
     */
    private void setupSystemSecurity() {
        biometricExecutor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, biometricExecutor, 
                new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                onOwnerVerified();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.w(TAG, "Biometric failed.");
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // On API 28, the 'Negative Button' click is used to fall back to System PIN
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showSystemCredentialPicker();
                } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    triggerIntruderAlert();
                }
            }
        });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("HFS Security")
                .setSubtitle("Authenticate to access your app");

        // CRITICAL FIX: Android 9 (API 28) does NOT support DEVICE_CREDENTIAL in this builder.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 and above logic
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG 
                                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            // Android 9 (Oppo CPH1937) logic
            // We must use Negative Button to launch the Device Credential Picker manually
            builder.setNegativeButtonText("Use System PIN");
        }

        promptInfo = builder.build();
    }

    /**
     * Fallback for Android 9: Opens the System PIN/Pattern/Password screen.
     */
    private void showSystemCredentialPicker() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isDeviceSecure()) {
            Intent intent = km.createConfirmDeviceCredentialIntent("HFS Security", "Enter your phone lock to proceed");
            if (intent != null) {
                startActivityForResult(intent, SYSTEM_CREDENTIAL_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SYSTEM_CREDENTIAL_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Success via System PIN/Pattern
                onOwnerVerified();
            } else {
                // User failed or canceled the System PIN
                triggerIntruderAlert();
            }
        }
    }

    private void triggerSystemAuth() {
        try {
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            // Fallback to PIN picker if Biometrics are not enrolled
            showSystemCredentialPicker();
        }
    }

    private void onOwnerVerified() {
        AppMonitorService.isLockActive = false;
        if (targetPackage != null) {
            AppMonitorService.unlockSession(targetPackage);
        }
        if (lastCapturedFrame != null) {
            lastCapturedFrame.close();
        }
        finish();
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

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (!isCameraCaptured) {
                        isCameraCaptured = true;
                        lastCapturedFrame = image;
                    } else {
                        image.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera Fail: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void checkMpinAndUnlock() {
        String input = binding.etPinInput.getText().toString();
        if (input.equals(db.getMasterPin())) {
            onOwnerVerified();
        } else {
            binding.tvErrorMsg.setText("Incorrect HFS MPIN");
            binding.etPinInput.setText("");
            triggerIntruderAlert();
        }
    }

    private void triggerIntruderAlert() {
        if (isActionTaken) return;
        isActionTaken = true;

        runOnUiThread(() -> {
            if (lastCapturedFrame != null) {
                FileSecureHelper.saveIntruderCapture(LockScreenActivity.this, lastCapturedFrame);
            }
            fetchLocationAndSendAlert();
            Toast.makeText(this, "⚠ Unauthorized access logged", Toast.LENGTH_LONG).show();
        });
    }

    private void fetchLocationAndSendAlert() {
        String appName = getIntent().getStringExtra("TARGET_APP_NAME");
        final String finalAppName = (appName == null) ? "a Protected App" : appName;

        LocationHelper.getDeviceLocation(this, new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationFound(String mapLink) {
                SmsHelper.sendAlertSms(LockScreenActivity.this, finalAppName, mapLink, "System Security Failure");
            }

            @Override
            public void onLocationFailed(String error) {
                SmsHelper.sendAlertSms(LockScreenActivity.this, finalAppName, "GPS Signal Lost", "System Security Failure");
            }
        });
    }

    @Override
    protected void onDestroy() {
        cameraExecutor.shutdown();
        AppMonitorService.isLockActive = false;
        if (lastCapturedFrame != null) {
            lastCapturedFrame.close();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {}
}