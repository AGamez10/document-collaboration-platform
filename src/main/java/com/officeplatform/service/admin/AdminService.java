package com.officeplatform.service.admin;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.officeplatform.dto.request.BackupConfigRequest;
import com.officeplatform.dto.request.CreateApiKeyRequest;
import com.officeplatform.dto.response.ActivityLogResponse;
import com.officeplatform.dto.response.AdminFileResponse;
import com.officeplatform.dto.response.ApiKeyCreatedResponse;
import com.officeplatform.dto.response.ApiKeyResponse;
import com.officeplatform.dto.response.BackupConfigResponse;
import com.officeplatform.dto.response.BackupInfoResponse;
import com.officeplatform.dto.response.DashboardResponse;
import com.officeplatform.dto.response.EditorSessionResponse;
import com.officeplatform.dto.response.KnownUserResponse;
import com.officeplatform.dto.response.OnlyOfficeStatusResponse;
import com.officeplatform.dto.response.PagedResponse;

public interface AdminService {

    DashboardResponse getDashboard();

    List<ApiKeyResponse> listApiKeys();

    ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request);

    ApiKeyResponse updateApiKeyStatus(Long id, boolean active);

    void deleteApiKey(Long id);

    PagedResponse<ActivityLogResponse> getActivityLog(
            Pageable pageable, LocalDate dateFrom, LocalDate dateTo, Long apiKeyId, String action, String userId,
            Long folderId);

    List<AdminFileResponse> listFiles(Long apiKeyId, boolean trashed);

    /** Devuelve un archivo a la papelera de su dueno; la restauracion final la hace el. */
    void restoreFile(Long fileId);

    /** Devuelve una carpeta a la papelera de su dueno; la restauracion final la hace el. */
    void restoreFolder(Long folderId);

    void purgeFile(Long fileId);

    OnlyOfficeStatusResponse getOnlyOfficeStatus();

    List<EditorSessionResponse> getEditorSessions(Boolean active, Long apiKeyId);

    void forceCloseSession(Long sessionId);

    int forceCloseAllSessions();

    List<KnownUserResponse> listUsers(Long apiKeyId, String search);

    /** Platform-admin operation: designate a known user's role ("user" | "admin") in its project. */
    KnownUserResponse updateUserRole(Long knownUserId, String role);

    List<BackupInfoResponse> listBackups();

    BackupInfoResponse createBackup(String type);

    InputStream getBackupStream(String fileName);

    /** Tamano en bytes del paquete, o -1 si no se pudo medir. */
    long getBackupSize(String fileName);

    void deleteBackup(String fileName);

    com.officeplatform.dto.response.BackupRestoreResponse restoreBackup(String fileName);

    com.officeplatform.dto.response.BackupRestoreResponse restoreFromUpload(
            String originalName, java.io.InputStream zipStream);

    BackupInfoResponse storeUploadedBackup(String originalName, java.io.InputStream zipStream);

    com.officeplatform.dto.response.PathValidationResponse validateBackupPath(String path);

    BackupConfigResponse getBackupConfig();

    BackupConfigResponse updateBackupConfig(BackupConfigRequest request);

    /** Prueba el destino de replicacion configurado escribiendo y borrando un archivo. */
    com.officeplatform.dto.response.PathValidationResponse testRemoteBackup();

}
