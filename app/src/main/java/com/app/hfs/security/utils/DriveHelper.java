package com.hfs.security.utils;

import android.content.Context;
import android.util.Log;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Google Drive REST API Utility.
 * This class handles:
 * 1. Searching for and creating the 'HFS Security' folder.
 * 2. Uploading intruder JPEG files to that specific folder.
 * 3. Setting public 'anyone with link' permissions for the file.
 * 4. Generating the final shareable URL for the SMS alert.
 */
public class DriveHelper {

    private static final String TAG = "HFS_DriveHelper";
    private static final String FOLDER_NAME = "HFS Security";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private final Drive driveService;
    private final HFSDatabaseHelper db;

    public DriveHelper(Context context, Drive driveService) {
        this.driveService = driveService;
        this.db = HFSDatabaseHelper.getInstance(context);
    }

    /**
     * Main task: Uploads a local file and returns its public shareable link.
     */
    public String uploadFileAndGetLink(java.io.File localFile) throws IOException {
        // 1. Ensure the HFS Security folder exists
        String folderId = getOrCreateHfsFolder();
        if (folderId == null) {
            throw new IOException("Failed to identify or create HFS Drive folder.");
        }

        // 2. Prepare File Metadata
        File fileMetadata = new File();
        fileMetadata.setName(localFile.getName());
        fileMetadata.setMimeType("image/jpeg");
        fileMetadata.setParents(Collections.singletonList(folderId));

        // 3. Prepare File Content
        FileContent mediaContent = new FileContent("image/jpeg", localFile);

        // 4. Execute Upload
        File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute();

        String fileId = uploadedFile.getId();
        Log.i(TAG, "File uploaded successfully. ID: " + fileId);

        // 5. Make the file public (Anyone with link can read)
        makeFilePublic(fileId);

        // 6. Return the finalized view link
        return uploadedFile.getWebViewLink();
    }

    /**
     * Logic: Searches for 'HFS Security' folder. Creates it if not found.
     */
    private String getOrCreateHfsFolder() throws IOException {
        // Check database first to avoid redundant API calls
        String savedFolderId = db.getDriveFolderId();
        if (savedFolderId != null && !savedFolderId.isEmpty()) {
            return savedFolderId;
        }

        // Search for folder by name
        String query = "name = '" + FOLDER_NAME + "' and mimeType = '" + FOLDER_MIME + "' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id)")
                .execute();

        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            String foundId = files.get(0).getId();
            db.saveDriveFolderId(foundId);
            return foundId;
        }

        // Create folder if it doesn't exist
        File folderMetadata = new File();
        folderMetadata.setName(FOLDER_NAME);
        folderMetadata.setMimeType(FOLDER_MIME);

        File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();

        if (folder != null) {
            db.saveDriveFolderId(folder.getId());
            Log.i(TAG, "Created new HFS Security folder on Drive.");
            return folder.getId();
        }

        return null;
    }

    /**
     * Logic: Sets permissions so the second phone doesn't need to log in to see the photo.
     * Role: reader | Type: anyone
     */
    private void makeFilePublic(String fileId) throws IOException {
        Permission permission = new Permission();
        permission.setRole("reader");
        permission.setType("anyone");

        driveService.permissions().create(fileId, permission).execute();
        Log.d(TAG, "Permissions updated: File is now public-viewable.");
    }
}