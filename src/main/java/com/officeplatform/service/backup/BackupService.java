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

    /** Tamano en bytes del paquete, para anunciar Content-Length en la descarga. */
    long getBackupSize(String fileName);

    void deleteBackup(String fileName);

    BackupConfigResponse getConfig();

    BackupConfigResponse updateConfig(BackupConfigRequest request);

    /**
     * Comprueba el destino remoto configurado escribiendo y borrando un archivo de prueba.
     *
     * <p>Distinto de validatePath: no evalua una ruta cualquiera sino la que la replicacion esta
     * usando ahora, que es lo que responde la pregunta "mi respaldo externo esta funcionando".
     */
    com.officeplatform.dto.response.PathValidationResponse testRemoteReplication();

    /** Comprueba que una ruta de destino exista (o pueda crearse) y admita escritura real. */
    com.officeplatform.dto.response.PathValidationResponse validatePath(String path);

    /** Restores a package already stored in the backup directory. */
    com.officeplatform.dto.response.BackupRestoreResponse restoreBackup(String fileName);

    /** Restores a package supplied by the caller without storing it first. */
    com.officeplatform.dto.response.BackupRestoreResponse restoreFromUpload(
            String originalName, java.io.InputStream zipStream);

    /** Stores an externally supplied package in the backup directory. */
    BackupInfoResponse storeUploadedBackup(String originalName, java.io.InputStream zipStream);

}
