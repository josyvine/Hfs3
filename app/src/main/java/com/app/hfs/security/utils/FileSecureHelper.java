package com.hfs.security.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data Storage Utility (Phase 6).
 * Manages the secret saving of intruder photos.
 * Data is stored in: /Android/data/com.hfs.security/files/intruders/
 * This location is hidden from standard Gallery apps.
 */
public class FileSecureHelper {

    private static final String TAG = "HFS_FileSecure";
    private static final String INTRUDER_DIR = "intruders";

    /**
     * Captures the current frame from the ImageProxy, converts it to a JPG,
     * and saves it secretly to the internal storage.
     * 
     * @param context App context.
     * @param imageProxy The frame from the front camera.
     */
    public static void saveIntruderCapture(Context context, ImageProxy imageProxy) {
        // 1. Convert ImageProxy to Bitmap
        Bitmap bitmap = imageProxyToBitmap(imageProxy);
        if (bitmap == null) return;

        // 2. Rotate bitmap if necessary (Front camera usually needs 270 deg rotation)
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        bitmap = rotateBitmap(bitmap, rotation);

        // 3. Prepare the Filename: AppName-PackageName-Timestamp.jpg
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Intrusion-" + timestamp + ".jpg";

        // 4. Get the secure internal directory
        File directory = new File(context.getExternalFilesDir(null), INTRUDER_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File(directory, fileName);

        // 5. Write the Bitmap to the file as a compressed JPEG
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            Log.i(TAG, "Intruder evidence saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save intruder photo: " + e.getMessage());
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Helper to convert CameraX YUV_420_888 format to Bitmap.
     */
    private static Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    /**
     * Rotates a bitmap to the correct orientation.
     */
    private static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        // Flip horizontally because it's a front camera (mirror effect)
        matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Deletes all intruder evidence logs.
     */
    public static void deleteAllLogs(Context context) {
        File directory = new File(context.getExternalFilesDir(null), INTRUDER_DIR);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
}