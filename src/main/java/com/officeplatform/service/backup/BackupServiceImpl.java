package com.officeplatform.service.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.officeplatform.dto.request.BackupConfigRequest;
import com.officeplatform.dto.response.BackupConfigResponse;
import com.officeplatform.dto.response.BackupInfoResponse;
import com.officeplatform.dto.response.BackupRestoreResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.service.storage.StorageService;

import lombok.extern.slf4j.Slf4j;

@Service
@EnableScheduling
@Slf4j
public class BackupServiceImpl implements BackupService {

    private static final DateTimeFormatter BACKUP_FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final KnownUserRepository knownUserRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final BackupRestoreService backupRestoreService;

    private String backupDirectoryPath;
    private boolean autoBackupEnabled;
    private int backupIntervalHours;
    private int maxRetainedBackups;
    private String replicationDirectoryPath;
    private boolean replicationEnabled;
    private LocalDateTime lastBackupTimestamp;

    public BackupServiceImpl(
            FileRepository fileRepository,
            FolderRepository folderRepository,
            ApiKeyRepository apiKeyRepository,
            KnownUserRepository knownUserRepository,
            StorageService storageService,
            @Value("${office-platform.backup.directory:backups}") String backupDirectoryPath,
            @Value("${office-platform.backup.auto-enabled:true}") boolean autoBackupEnabled,
            @Value("${office-platform.backup.interval-hours:24}") int backupIntervalHours,
            @Value("${office-platform.backup.max-retained:10}") int maxRetainedBackups,
            @Value("${office-platform.backup.replication-directory:}") String replicationDirectoryPath,
            @Value("${office-platform.backup.replication-enabled:false}") boolean replicationEnabled,
            BackupRestoreService backupRestoreService) {
        this.replicationDirectoryPath = replicationDirectoryPath;
        this.replicationEnabled = replicationEnabled;
        this.backupRestoreService = backupRestoreService;

        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.knownUserRepository = knownUserRepository;
        this.storageService = storageService;

        this.backupDirectoryPath = backupDirectoryPath;
        this.autoBackupEnabled = autoBackupEnabled;
        this.backupIntervalHours = backupIntervalHours;
        this.maxRetainedBackups = maxRetainedBackups;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        initDirectory();
    }

    private synchronized Path getResolvedDirectory() {
        Path path = Paths.get(backupDirectoryPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                log.warn("No se pudo crear el directorio de backups en {}. Usando fallback temporal: {}", path, e.getMessage());
                path = Paths.get(System.getProperty("java.io.tmpdir"), "office_backups").toAbsolutePath().normalize();
                try {
                    Files.createDirectories(path);
                } catch (Exception ex) {
                    log.error("Fallback de directorio también falló: {}", ex.getMessage());
                }
            }
        }
        return path;
    }

    private void initDirectory() {
        Path dir = getResolvedDirectory();
        log.info("Directorio de copias de seguridad inicializado en: {}", dir);
    }

    @Override
    public List<BackupInfoResponse> listBackups() {
        Path dir = getResolvedDirectory();
        File folder = dir.toFile();
        File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".zip"));

        if (files == null || files.length == 0) {
            return List.of();
        }

        List<BackupInfoResponse> list = new ArrayList<>();
        for (File file : files) {
            LocalDateTime createdAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(file.lastModified()),
                    ZoneId.systemDefault());

            String type = file.getName().contains("auto") ? "AUTOMATICO" : "MANUAL";

            list.add(BackupInfoResponse.builder()
                    .fileName(file.getName())
                    .sizeBytes(file.length())
                    .formattedSize(formatBytes(file.length()))
                    .filesCount(estimateFilesCount(file.getName()))
                    .type(type)
                    .createdAt(createdAt)
                    .build());
        }

        list.sort(Comparator.comparing(BackupInfoResponse::getCreatedAt).reversed());
        return list;
    }

    @Override
    public synchronized BackupInfoResponse createBackup(String type) {
        Path dir = getResolvedDirectory();
        LocalDateTime now = LocalDateTime.now();
        String prefix = (type != null && type.toUpperCase().contains("AUTO")) ? "backup_auto_" : "backup_manual_";
        String zipName = prefix + now.format(BACKUP_FILE_DATE_FORMAT) + ".zip";
        Path zipPath = dir.resolve(zipName);

        log.info("Iniciando copia de seguridad {} en {}", type, zipPath);

        List<FileEntity> allFiles = fileRepository.findAll();
        List<FolderEntity> allFolders = folderRepository.findAll();
        List<ApiKeyEntity> allApiKeys = apiKeyRepository.findAll();
        List<KnownUserEntity> allUsers = knownUserRepository.findAll();

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("backupType", type);
        manifest.put("createdAt", now.toString());
        manifest.put("totalFiles", allFiles.size());
        manifest.put("totalFolders", allFolders.size());
        manifest.put("totalApiKeys", allApiKeys.size());
        manifest.put("totalKnownUsers", allUsers.size());
        manifest.put("filesMetadata", allFiles);
        manifest.put("foldersMetadata", allFolders);
        manifest.put("apiKeysMetadata", allApiKeys);
        manifest.put("knownUsersMetadata", allUsers);

        int writtenFiles = 0;
        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. Write manifest.json
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
            zos.write(manifestBytes);
            zos.closeEntry();

            // 2. Stream binaries from MinIO
            for (FileEntity file : allFiles) {
                if (file.getObjectName() == null || file.getObjectName().isBlank()) {
                    continue;
                }
                try (InputStream stream = storageService.retrieve(file.getObjectName())) {
                    String entryPath = "files/" + file.getObjectName();
                    ZipEntry fileEntry = new ZipEntry(entryPath);
                    zos.putNextEntry(fileEntry);
                    stream.transferTo(zos);
                    zos.closeEntry();
                    writtenFiles++;
                } catch (Exception ex) {
                    log.warn("No se pudo incluir el archivo binario {} en el backup: {}", file.getObjectName(), ex.getMessage());
                }
            }

            zos.finish();
        } catch (IOException e) {
            log.error("Error crítico creando backup: {}", e.getMessage(), e);
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException ignored) {}
            throw new StorageException("No se pudo generar la copia de seguridad: " + e.getMessage());
        }

        this.lastBackupTimestamp = now;
        replicateToSecondary(zipPath, zipName);
        enforceRetentionPolicy();

        File createdFile = zipPath.toFile();
        return BackupInfoResponse.builder()
                .fileName(zipName)
                .sizeBytes(createdFile.length())
                .formattedSize(formatBytes(createdFile.length()))
                .filesCount(writtenFiles)
                .type(type != null ? type.toUpperCase() : "MANUAL")
                .createdAt(now)
                .build();
    }

    @Override
    public InputStream getBackupStream(String fileName) {
        validateFileName(fileName);
        Path filePath = getResolvedDirectory().resolve(fileName).normalize();
        if (!Files.exists(filePath)) {
            throw new StorageException("El archivo de copia de seguridad no existe");
        }
        try {
            return new FileInputStream(filePath.toFile());
        } catch (IOException e) {
            throw new StorageException("Error leyendo la copia de seguridad: " + e.getMessage());
        }
    }

    @Override
    public long getBackupSize(String fileName) {
        validateFileName(fileName);
        Path filePath = getResolvedDirectory().resolve(fileName).normalize();
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            // Un tamano desconocido no justifica romper la descarga: se responde sin
            // Content-Length y el navegador la baja igual, solo que sin barra de progreso.
            log.warn("No se pudo medir la copia de seguridad {}: {}", fileName, e.getMessage());
            return -1L;
        }
    }

    @Override
    public void deleteBackup(String fileName) {
        validateFileName(fileName);
        Path filePath = getResolvedDirectory().resolve(fileName).normalize();
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (!deleted) {
                throw new StorageException("El archivo de backup no existe");
            }
            log.info("Copia de seguridad eliminada: {}", fileName);
        } catch (IOException e) {
            throw new StorageException("No se pudo eliminar el backup: " + e.getMessage());
        }
    }

    @Override
    public BackupConfigResponse getConfig() {
        Path dir = getResolvedDirectory();
        File rootDir = dir.toFile();
        long freeBytes = rootDir.getFreeSpace();
        long totalBytes = rootDir.getTotalSpace();

        return BackupConfigResponse.builder()
                .enabled(autoBackupEnabled)
                .intervalHours(backupIntervalHours)
                .maxRetainedBackups(maxRetainedBackups)
                .backupDirectory(dir.toString())
                .replicationDirectory(replicationDirectoryPath)
                .replicationEnabled(replicationEnabled)
                .replicationReachable(isReplicationReachable())
                .diskFreeSpaceBytes(freeBytes)
                .formattedDiskFreeSpace(formatBytes(freeBytes))
                .diskTotalSpaceBytes(totalBytes)
                .formattedDiskTotalSpace(formatBytes(totalBytes))
                .build();
    }

    @Override
    public synchronized BackupConfigResponse updateConfig(BackupConfigRequest request) {
        this.autoBackupEnabled = request.getEnabled();
        this.backupIntervalHours = request.getIntervalHours();
        this.maxRetainedBackups = request.getMaxRetainedBackups();

        // Paths are applied hot so an operator can move the destination without a redeploy.
        // A blank value means "leave as is" rather than "clear": clearing the primary directory
        // would silently send the next package somewhere unexpected.
        if (request.getBackupDirectory() != null && !request.getBackupDirectory().isBlank()) {
            this.backupDirectoryPath = request.getBackupDirectory().trim();
            initDirectory();
        }
        if (request.getReplicationDirectory() != null) {
            this.replicationDirectoryPath = request.getReplicationDirectory().trim();
        }
        if (request.getReplicationEnabled() != null) {
            this.replicationEnabled = request.getReplicationEnabled();
        }

        log.info("Configuración de backups actualizada: habilitado={}, intervalo={}h, retención={}, "
                        + "directorio={}, replicación={} hacia '{}'",
                autoBackupEnabled, backupIntervalHours, maxRetainedBackups,
                backupDirectoryPath, replicationEnabled, replicationDirectoryPath);

        enforceRetentionPolicy();
        return getConfig();
    }

    @Scheduled(fixedDelay = 60000) // cada minuto revisa si corresponde ejecutar el backup automático
    public void checkAndRunScheduledBackup() {
        if (!autoBackupEnabled) {
            return;
        }

        if (lastBackupTimestamp == null) {
            // Find most recent file timestamp if available
            List<BackupInfoResponse> existing = listBackups();
            if (!existing.isEmpty()) {
                lastBackupTimestamp = existing.get(0).getCreatedAt();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (lastBackupTimestamp == null || lastBackupTimestamp.plusHours(backupIntervalHours).isBefore(now)) {
            log.info("Ejecutando backup automático programado...");
            try {
                createBackup("AUTOMATICO");
            } catch (Exception e) {
                log.error("Error en backup automático programado: {}", e.getMessage());
            }
        }
    }

    private void enforceRetentionPolicy() {
        if (maxRetainedBackups <= 0) return;

        Path dir = getResolvedDirectory();
        File folder = dir.toFile();
        File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".zip"));
        if (files == null || files.length <= maxRetainedBackups) {
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int toDelete = files.length - maxRetainedBackups;
        for (int i = 0; i < toDelete; i++) {
            File oldFile = files[i];
            try {
                Files.deleteIfExists(oldFile.toPath());
                log.info("Backup antiguo eliminado por política de retención: {}", oldFile.getName());
            } catch (IOException e) {
                log.warn("No se pudo eliminar backup antiguo {}: {}", oldFile.getName(), e.getMessage());
            }
        }
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new StorageException("Nombre de archivo de copia de seguridad inválido");
        }
    }

    private Integer estimateFilesCount(String zipName) {
        return null; // El frontend lo manejará limpiamente
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        double size = bytes / Math.pow(1024, digitGroups);
        return String.format("%.2f %s", size, units[digitGroups]);
    }


    /**
     * Copies a freshly written package to the secondary location (NAS / DataServer).
     *
     * <p>Failures are logged and swallowed on purpose. The local package is already on disk and
     * valid; if the network share is down, losing the replica is bad but destroying the backup run
     * because of it would be worse — the operator would end up with neither copy.
     */
    private void replicateToSecondary(Path sourceZip, String zipName) {
        if (!replicationEnabled || replicationDirectoryPath == null || replicationDirectoryPath.isBlank()) {
            return;
        }
        try {
            Path target = Paths.get(replicationDirectoryPath.trim());
            Files.createDirectories(target);
            Path destination = target.resolve(zipName);
            Files.copy(sourceZip, destination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copia de seguridad replicada en '{}'", destination);
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo replicar la copia '{}' hacia '{}': {}. "
                            + "El respaldo local se conservó intacto.",
                    zipName, replicationDirectoryPath, e.getMessage());
        }
    }

    /** Whether the secondary location can be written to right now, for display in the console. */
    private boolean isReplicationReachable() {
        if (replicationDirectoryPath == null || replicationDirectoryPath.isBlank()) {
            return false;
        }
        try {
            Path target = Paths.get(replicationDirectoryPath.trim());
            return Files.isDirectory(target) && Files.isWritable(target);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public BackupRestoreResponse restoreBackup(String fileName) {
        validateFileName(fileName);
        Path zipPath = getResolvedDirectory().resolve(fileName).normalize();
        return backupRestoreService.restore(zipPath, fileName);
    }

    @Override
    public BackupRestoreResponse restoreFromUpload(String originalName, InputStream zipStream) {
        String label = (originalName == null || originalName.isBlank()) ? "respaldo-externo.zip" : originalName;
        return backupRestoreService.restore(zipStream, label);
    }

    @Override
    public BackupInfoResponse storeUploadedBackup(String originalName, InputStream zipStream) {
        String safeName = sanitizeUploadName(originalName);
        Path destination = getResolvedDirectory().resolve(safeName).normalize();
        try {
            Files.copy(zipStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("No se pudo guardar el respaldo subido: " + e.getMessage());
        }
        File stored = destination.toFile();
        log.info("Respaldo externo almacenado como '{}'", safeName);
        return BackupInfoResponse.builder()
                .fileName(safeName)
                .sizeBytes(stored.length())
                .formattedSize(formatBytes(stored.length()))
                .type("MANUAL")
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Reduces an uploaded name to a safe file name inside the backup directory: no path
     * separators, no traversal, and always a .zip extension.
     */
    private String sanitizeUploadName(String originalName) {
        String base = (originalName == null || originalName.isBlank()) ? "respaldo-externo" : originalName;
        base = base.replace("\\", "/");
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank() || base.startsWith(".")) {
            base = "respaldo-externo";
        }
        if (!base.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            base = base + ".zip";
        }
        return "backup_externo_" + LocalDateTime.now().format(BACKUP_FILE_DATE_FORMAT) + "_" + base;
    }

    /**
     * Checks a destination before it is saved.
     *
     * <p>Writes and deletes a probe file rather than trusting {@code Files.isWritable}: on a
     * Windows network share that flag routinely reports true for a path the process cannot
     * actually write to, and the failure would only surface when a backup silently stopped
     * being produced.
     */
    @Override
    public com.officeplatform.dto.response.PathValidationResponse validatePath(String rawPath) {
        String candidate = (rawPath == null) ? "" : rawPath.trim();
        if (candidate.isEmpty()) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path(candidate)
                    .message("Indicá una ruta de destino.")
                    .build();
        }

        Path target;
        try {
            target = Paths.get(candidate).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path(candidate)
                    .message("La ruta no tiene un formato válido para este sistema.")
                    .build();
        }

        boolean created = false;
        if (!Files.exists(target)) {
            try {
                Files.createDirectories(target);
                created = true;
            } catch (IOException e) {
                return com.officeplatform.dto.response.PathValidationResponse.builder()
                        .valid(false).path(target.toString())
                        .message("No se pudo crear el directorio: " + e.getMessage())
                        .build();
            }
        } else if (!Files.isDirectory(target)) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path(target.toString())
                    .message("La ruta existe pero es un archivo, no un directorio.")
                    .build();
        }

        Path probe = target.resolve(".office-platform-write-test");
        try {
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path(target.toString()).created(created).writable(false)
                    .message("El directorio existe pero no se puede escribir en él: " + e.getMessage())
                    .build();
        }

        long free = target.toFile().getFreeSpace();
        return com.officeplatform.dto.response.PathValidationResponse.builder()
                .valid(true)
                .path(target.toString())
                .created(created)
                .writable(true)
                .freeSpaceBytes(free)
                .formattedFreeSpace(formatBytes(free))
                .message(created
                        ? "Directorio creado y accesible. " + formatBytes(free) + " libres."
                        : "Ruta accesible. " + formatBytes(free) + " libres.")
                .build();
    }

}
