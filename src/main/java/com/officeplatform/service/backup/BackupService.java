package com.officeplatform.service.backup;

import java.io.InputStream;
import java.util.List;

import com.officeplatform.dto.request.BackupConfigRequest;
import com.officeplatform.dto.response.BackupConfigResponse;
import com.officeplatform.dto.response.BackupInfoResponse;

public interface BackupService {

    List<BackupInfoResponse> listBackups();

    BackupInfoResponse createBackup(String type);

    InputStream getBackupStream(String fileName);

    void deleteBackup(String fileName);

    BackupConfigResponse getConfig();

    BackupConfigResponse updateConfig(BackupConfigRequest request);

    /** Restores a package already stored in the backup directory. */
    com.officeplatform.dto.response.BackupRestoreResponse restoreBackup(String fileName);

    /** Restores a package supplied by the caller without storing it first. */
    com.officeplatform.dto.response.BackupRestoreResponse restoreFromUpload(
            String originalName, java.io.InputStream zipStream);

    /** Stores an externally supplied package in the backup directory. */
    BackupInfoResponse storeUploadedBackup(String originalName, java.io.InputStream zipStream);

}
