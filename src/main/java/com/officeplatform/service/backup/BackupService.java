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

}
