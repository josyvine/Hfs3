package com.hfs.security.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/**
 * Global Persistent Storage Manager for HFS Security.
 * UPDATED for "Zero-Fail" Plan:
 * 1. Stores complex 5-point normalized biometric signatures.
 * 2. Tracks hardware capability (Class 3 Face support status).
 * 3. Manages session states for app locking and alert cooldowns.
 */
public class HFSDatabaseHelper {

    private static final String PREF_NAME = "hfs_security_prefs_v2";
    
    // Storage Keys
    private static final String KEY_PROTECTED_PACKAGES = "protected_packages";
    private static final String KEY_MASTER_PIN = "master_pin";
    private static final String KEY_TRUSTED_NUMBER = "trusted_number";
    private static final String KEY_SETUP_COMPLETE = "setup_complete";
    private static final String KEY_STEALTH_MODE = "stealth_mode_enabled";
    private static final String KEY_FAKE_GALLERY = "fake_gallery_enabled";
    
    // UPDATED: Stores the Normalized 5-Point Geometric Map (avgEE|avgEN|avgMW)
    private static final String KEY_OWNER_FACE_DATA = "owner_face_triangulation_map";
    
    // NEW: Stores the result of Step 1 (Hardware Capability Check)
    private static final String KEY_HW_FACE_SUPPORT = "hardware_face_support_status";

    private static HFSDatabaseHelper instance;
    private final SharedPreferences prefs;
    private final Gson gson;

    private HFSDatabaseHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized HFSDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new HFSDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    // --- BIOMETRIC SIGNATURE STORAGE ---

    /**
     * Saves the averaged, normalized geometric face map.
     * Format: "RatioEyeEye|RatioEyeNose|RatioMouthWidth"
     */
    public void saveOwnerFaceData(String signatureMap) {
        prefs.edit().putString(KEY_OWNER_FACE_DATA, signatureMap).apply();
    }

    public String getOwnerFaceData() {
        return prefs.getString(KEY_OWNER_FACE_DATA, "");
    }

    // --- HARDWARE CAPABILITY TRACKING ---

    /**
     * Records if the phone has Class 3 (Secure) Face Hardware.
     * Determined by Step 1 of the Security Plan.
     */
    public void setHardwareFaceSupported(boolean supported) {
        prefs.edit().putBoolean(KEY_HW_FACE_SUPPORT, supported).apply();
    }

    public boolean isHardwareFaceSupported() {
        return prefs.getBoolean(KEY_HW_FACE_SUPPORT, false);
    }

    // --- PROTECTED APPS MANAGEMENT ---

    public void saveProtectedPackages(Set<String> packages) {
        String json = gson.toJson(packages);
        prefs.edit().putString(KEY_PROTECTED_PACKAGES, json).apply();
    }

    public Set<String> getProtectedPackages() {
        String json = prefs.getString(KEY_PROTECTED_PACKAGES, null);
        if (json == null) {
            return new HashSet<>();
        }
        Type type = new TypeToken<HashSet<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public int getProtectedAppsCount() {
        return getProtectedPackages().size();
    }

    // --- SECURITY CREDENTIALS ---

    public void saveMasterPin(String pin) {
        prefs.edit().putString(KEY_MASTER_PIN, pin).apply();
    }

    public String getMasterPin() {
        return prefs.getString(KEY_MASTER_PIN, "0000");
    }

    public void saveTrustedNumber(String number) {
        prefs.edit().putString(KEY_TRUSTED_NUMBER, number).apply();
    }

    public String getTrustedNumber() {
        return prefs.getString(KEY_TRUSTED_NUMBER, "");
    }

    // --- SYSTEM STATES ---

    public boolean isSetupComplete() {
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false);
    }

    public void setSetupComplete(boolean status) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, status).apply();
    }

    public void setStealthMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_STEALTH_MODE, enabled).apply();
    }

    public boolean isStealthModeEnabled() {
        return prefs.getBoolean(KEY_STEALTH_MODE, false);
    }

    public void setFakeGalleryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FAKE_GALLERY, enabled).apply();
    }

    public boolean isFakeGalleryEnabled() {
        return prefs.getBoolean(KEY_FAKE_GALLERY, false);
    }

    /**
     * Resets all app data and security credentials.
     */
    public void clearAllData() {
        prefs.edit().clear().apply();
    }
}