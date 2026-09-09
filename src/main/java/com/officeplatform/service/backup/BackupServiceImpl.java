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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.repository.SharePermissionRepository;
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
    private final SharePermissionRepository sharePermissionRepository;
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
            SharePermissionRepository sharePermissionRepository,
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
        this.sharePermissionRepository = sharePermissionRepository;
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
        List<SharePermissionEntity> allSharePermissions = sharePermissionRepository.findAll();

        // Las rutas se resuelven antes de escribir nada porque el manifest va primero en el ZIP y
        // tiene que declarar dónde quedó cada binario.
        Map<Long, FolderEntity> foldersById = new HashMap<>();
        for (FolderEntity folder : allFolders) {
            if (folder.getId() != null) {
                foldersById.put(folder.getId(), folder);
            }
        }
        Map<Long, ApiKeyEntity> apiKeysById = new HashMap<>();
        for (ApiKeyEntity apiKey : allApiKeys) {
            if (apiKey.getId() != null) {
                apiKeysById.put(apiKey.getId(), apiKey);
            }
        }

        // El nombre visible de cada persona, para que la carpeta del ZIP diga "1004356866 - Ana
        // Pérez" y no una cédula suelta que nadie reconoce al abrir el paquete.
        Map<String, String> displayNames = new HashMap<>();
        for (KnownUserEntity user : allUsers) {
            if (user.getUserId() == null) {
                continue;
            }
            String name = user.getDisplayName();
            if (name == null || name.isBlank()) {
                continue;
            }
            displayNames.put(user.getApiKeyId() + "|" + user.getUserId().trim(), name);
            displayNames.putIfAbsent(user.getUserId().trim(), name);
        }

        Set<String> usedPaths = new HashSet<>();
        Map<Long, String> pathByFileId = new HashMap<>();
        List<Map<String, Object>> filesMetadata = new ArrayList<>(allFiles.size());
        for (FileEntity file : allFiles) {
            @SuppressWarnings("unchecked")
            Map<String, Object> node = objectMapper.convertValue(file, Map.class);
            if (file.getObjectName() != null && !file.getObjectName().isBlank()) {
                String entryPath = buildHierarchicalPath(file, foldersById, apiKeysById, displayNames, usedPaths);
                node.put("zipEntryPath", entryPath);
                if (file.getId() != null) {
                    pathByFileId.put(file.getId(), entryPath);
                }
            }
            filesMetadata.add(node);
        }

        // Vista de contingencia: copias de lo que a cada persona le compartieron, para que al abrir
        // el ZIP sin servidor encuentre en su propia carpeta lo que en el gestor ve bajo
        // "Compartidos conmigo". Estas rutas NO viajan en filesMetadata a propósito: son copias
        // para el humano, y declararlas haría que la restauración duplicara filas y objetos.
        Map<Long, List<String>> sharedCopies = buildSharedWithMeCopies(
                allSharePermissions, allFiles, foldersById, apiKeysById, displayNames, usedPaths);

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("backupType", type);
        manifest.put("createdAt", now.toString());
        // Marca de formato: 2 introdujo zipEntryPath, 3 agrega los permisos y la segmentación por
        // usuario. El restaurador la lee para saber en qué puede confiar.
        manifest.put("layoutVersion", 3);
        manifest.put("totalFiles", allFiles.size());
        manifest.put("totalFolders", allFolders.size());
        manifest.put("totalApiKeys", allApiKeys.size());
        manifest.put("totalKnownUsers", allUsers.size());
        manifest.put("totalSharePermissions", allSharePermissions.size());
        manifest.put("filesMetadata", filesMetadata);
        manifest.put("foldersMetadata", allFolders);
        manifest.put("apiKeysMetadata", allApiKeys);
        manifest.put("knownUsersMetadata", allUsers);
        // Sin esto, restaurar devolvía los archivos pero abría el acceso: cada restricción, nota y
        // vencimiento desaparecía, y lo que estaba limitado a una persona quedaba a la vista.
        manifest.put("sharePermissionsMetadata", allSharePermissions);

        int writtenFiles = 0;
        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. Write manifest.json
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
            zos.write(manifestBytes);
            zos.closeEntry();

            // 2. Los binarios van en su ruta legible: quien abra el ZIP en el Explorador tiene que
            // encontrar sus proyectos y carpetas, no 300 UUID sueltos. Es la copia de contingencia
            // para trabajar sin la plataforma, así que el nombre real no es cosmético.
            for (FileEntity file : allFiles) {
                if (file.getObjectName() == null || file.getObjectName().isBlank()) {
                    continue;
                }
                String entryPath = file.getId() == null ? null : pathByFileId.get(file.getId());
                if (entryPath == null) {
                    entryPath = "archivos/_sin_ubicacion/" + safeName(file.getObjectName());
                }
                List<String> copies = file.getId() == null
                        ? List.of() : sharedCopies.getOrDefault(file.getId(), List.of());
                try (InputStream stream = storageService.retrieve(file.getObjectName())) {
                    if (copies.isEmpty()) {
                        // Sin copias se transfiere en streaming: no hay motivo para cargar en
                        // memoria un archivo que se escribe una sola vez.
                        zos.putNextEntry(new ZipEntry(entryPath));
                        stream.transferTo(zos);
                        zos.closeEntry();
                    } else {
                        // Con copias hay que leerlo una vez y escribirlo varias: volver a pedirlo a
                        // MinIO por cada destinatario multiplicaría las descargas sin ganar nada.
                        byte[] content = stream.readAllBytes();
                        zos.putNextEntry(new ZipEntry(entryPath));
                        zos.write(content);
                        zos.closeEntry();
                        for (String copy : copies) {
                            zos.putNextEntry(new ZipEntry(copy));
                            zos.write(content);
                            zos.closeEntry();
                        }
                    }
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

    /** Profundidad máxima de carpetas que se recorre antes de asumir que la cadena está rota. */
    private static final int MAX_FOLDER_DEPTH = 32;

    // Tramos fijos del árbol del ZIP. Reproducen las secciones del gestor para que abrir el
    // paquete en el Explorador se parezca a mirar la aplicación.
    private static final String AREA_SEGMENT = "Compartidos por Area";
    private static final String USERS_SEGMENT = "Usuarios";
    private static final String MY_FILES_SEGMENT = "Mis Archivos";
    private static final String SHARED_WITH_ME_SEGMENT = "Compartidos conmigo";

    /**
     * Ruta legible donde vive el binario de un archivo dentro del ZIP.
     *
     * <p>Reconstruye {@code archivos/{proyecto}/{carpeta}/.../{nombre real}} caminando la cadena de
     * padres. Un backup se abre en el Explorador de Windows justo cuando la plataforma no está
     * disponible: si ahí aparecen 300 UUID planos, el paquete es técnicamente correcto e
     * inservible para la persona que necesita su planilla.
     *
     * @param usedPaths rutas ya ocupadas; dos archivos con el mismo nombre en la misma carpeta
     *                  colisionarían y el segundo sobrescribiría la entrada del primero
     */
    private String buildHierarchicalPath(FileEntity file,
                                         Map<Long, FolderEntity> foldersMap,
                                         Map<Long, ApiKeyEntity> apiKeysMap,
                                         Map<String, String> displayNames,
                                         Set<String> usedPaths) {
        StringBuilder path = new StringBuilder("archivos/");
        path.append(projectSegment(file.getApiKeyId(), apiKeysMap)).append('/');

        // La segmentación copia lo que la persona ve en el gestor. Un archivo sin userId vive en el
        // espacio compartido del área; uno con userId es privado de esa persona, y mezclarlos en la
        // misma carpeta del ZIP haría que un respaldo abierto en Windows exponga lo de cada uno
        // junto a lo de todos.
        boolean shared = file.getUserId() == null;
        if (shared) {
            path.append(AREA_SEGMENT).append('/');
        } else {
            path.append(USERS_SEGMENT).append('/')
                .append(userFolderName(file.getUserId(), file.getApiKeyId(), displayNames)).append('/')
                .append(MY_FILES_SEGMENT).append('/');
        }

        for (String segment : folderChain(file.getFolderId(), foldersMap, shared)) {
            path.append(segment).append('/');
        }

        path.append(safeName(readableName(file)));
        return deduplicate(path.toString(), usedPaths);
    }

    /** Nombre del proyecto tal como se muestra, o un marcador estable si ya no existe. */
    private static String projectSegment(Long apiKeyId, Map<Long, ApiKeyEntity> apiKeysMap) {
        ApiKeyEntity project = apiKeyId == null ? null : apiKeysMap.get(apiKeyId);
        String name = project == null ? null : project.getName();
        if (name == null || name.isBlank()) {
            return safeName(apiKeyId == null ? "sin-proyecto" : "proyecto-" + apiKeyId);
        }
        return safeName(name);
    }

    /** "1004356866 - Ana Pérez", o solo la cédula cuando no se conoce el nombre. */
    private static String userFolderName(String userId, Long apiKeyId, Map<String, String> displayNames) {
        String trimmed = userId == null ? "" : userId.trim();
        String name = displayNames.get(apiKeyId + "|" + trimmed);
        if (name == null) {
            name = displayNames.get(trimmed);
        }
        return (name == null || name.isBlank())
                ? safeName(trimmed)
                : safeName(trimmed) + " - " + safeName(name);
    }

    private static String readableName(FileEntity file) {
        String name = file.getOriginalFileName();
        return (name == null || name.isBlank()) ? file.getObjectName() : name;
    }

    /**
     * Segmentos de carpeta desde la raíz hasta la que contiene el archivo, ya saneados.
     *
     * @param sharedSegment cuando es true se omiten las carpetas privadas de la cadena: el tramo
     *                      del área no debe llevar el nombre de la carpeta personal de nadie
     */
    private List<String> folderChain(Long folderId, Map<Long, FolderEntity> foldersMap, boolean sharedSegment) {
        List<String> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long current = folderId;
        // Se sube hasta la raíz y después se invierte. El guard de ciclos no es paranoia: una
        // cadena de padres corrupta colgaría la generación del backup entero.
        while (current != null && seen.add(current) && chain.size() < MAX_FOLDER_DEPTH) {
            FolderEntity folder = foldersMap.get(current);
            if (folder == null) {
                break;
            }
            if (!sharedSegment || folder.getUserId() == null) {
                chain.add(safeName(folder.getName()));
            }
            current = folder.getParentId();
        }
        Collections.reverse(chain);
        return chain;
    }

    /**
     * Rutas extra donde copiar cada binario para reconstruir "Compartidos conmigo" dentro del ZIP.
     *
     * <p>Solo se atienden los permisos dirigidos a una persona. Un permiso sobre una carpeta se
     * expande a los archivos que contiene, porque quien abre el paquete busca los documentos, no la
     * carpeta vacía. Los permisos a nivel de proyecto quedan fuera a propósito: replicarlos
     * duplicaría el paquete entero sin darle nada nuevo a nadie.
     *
     * @return rutas adicionales por id de archivo
     */
    private Map<Long, List<String>> buildSharedWithMeCopies(
            List<SharePermissionEntity> permissions,
            List<FileEntity> allFiles,
            Map<Long, FolderEntity> foldersMap,
            Map<Long, ApiKeyEntity> apiKeysMap,
            Map<String, String> displayNames,
            Set<String> usedPaths) {

        Map<Long, List<String>> copies = new HashMap<>();
        if (permissions == null || permissions.isEmpty()) {
            return copies;
        }
        Map<Long, FileEntity> filesById = new HashMap<>();
        Map<Long, List<FileEntity>> filesByFolder = new HashMap<>();
        for (FileEntity file : allFiles) {
            if (file.getId() != null) {
                filesById.put(file.getId(), file);
            }
            if (file.getFolderId() != null) {
                filesByFolder.computeIfAbsent(file.getFolderId(), k -> new ArrayList<>()).add(file);
            }
        }

        for (SharePermissionEntity permission : permissions) {
            if (permission.getTargetType() != SharePermissionEntity.TargetType.USER
                    || permission.getTargetUserId() == null
                    || permission.getTargetUserId().isBlank()
                    || permission.getResourceId() == null) {
                continue;
            }
            List<FileEntity> shared = permission.getResourceType() == SharePermissionEntity.ResourceType.FILE
                    ? oneFile(filesById.get(permission.getResourceId()))
                    : filesUnder(permission.getResourceId(), foldersMap, filesByFolder);

            for (FileEntity file : shared) {
                if (file.getId() == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
                    continue;
                }
                String path = "archivos/" + projectSegment(file.getApiKeyId(), apiKeysMap)
                        + "/" + USERS_SEGMENT
                        + "/" + userFolderName(permission.getTargetUserId(), permission.getSourceApiKeyId(), displayNames)
                        + "/" + SHARED_WITH_ME_SEGMENT
                        + "/" + safeName(readableName(file));
                copies.computeIfAbsent(file.getId(), k -> new ArrayList<>())
                        .add(deduplicate(path, usedPaths));
            }
        }
        return copies;
    }

    private static List<FileEntity> oneFile(FileEntity file) {
        return file == null ? List.of() : List.of(file);
    }

    /** Archivos contenidos en una carpeta y en todo su subárbol. */
    private static List<FileEntity> filesUnder(Long folderId,
                                               Map<Long, FolderEntity> foldersMap,
                                               Map<Long, List<FileEntity>> filesByFolder) {
        List<FileEntity> found = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        List<Long> queue = new ArrayList<>(List.of(folderId));
        while (!queue.isEmpty()) {
            Long current = queue.remove(queue.size() - 1);
            if (!visited.add(current)) {
                continue;
            }
            found.addAll(filesByFolder.getOrDefault(current, List.of()));
            for (FolderEntity folder : foldersMap.values()) {
                if (current.equals(folder.getParentId()) && folder.getId() != null) {
                    queue.add(folder.getId());
                }
            }
        }
        return found;
    }

    /**
     * Convierte un nombre en un segmento de ruta que Windows y el ZIP aceptan.
     *
     * <p>Windows rechaza {@code \ / : * ? " < > |} y no tolera un punto o un espacio al final del
     * nombre: una carpeta llamada "Ventas." queda inaccesible al extraer.
     */
    static String safeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "sin-nombre";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            out.append((c < 0x20 || "\\/:*?\"<>|".indexOf(c) >= 0) ? '_' : c);
        }
        String cleaned = out.toString().trim();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isBlank()) {
            return "sin-nombre";
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    /** Agrega un sufijo hasta encontrar una ruta libre, conservando la extensión. */
    private static String deduplicate(String path, Set<String> usedPaths) {
        if (usedPaths.add(path)) {
            return path;
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        String base = (dot > slash) ? path.substring(0, dot) : path;
        String extension = (dot > slash) ? path.substring(dot) : "";
        for (int i = 2; i < 10000; i++) {
            String candidate = base + " (" + i + ")" + extension;
            if (usedPaths.add(candidate)) {
                return candidate;
            }
        }
        // Inalcanzable con menos de 10000 homónimos en la misma carpeta, pero no se devuelve una
        // ruta repetida: sobrescribiría la entrada anterior y se perdería un archivo sin aviso.
        String fallback = base + " (" + System.nanoTime() + ")" + extension;
        usedPaths.add(fallback);
        return fallback;
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
    /**
     * Prueba el destino remoto que la replicacion esta usando ahora mismo.
     *
     * <p>Acepta rutas UNC corporativas del tipo {@code \\NAS\backups}, con una advertencia que
     * el codigo debe dar en vez de dejar que se descubra tarde: la aplicacion corre dentro de un
     * contenedor Linux, y ahi una ruta UNC no significa nada. Para que funcione, el recurso de red
     * tiene que estar montado en el host y ese punto de montaje bind-mounteado en el contenedor.
     * Sin eso el destino simplemente no existe, y este endpoint lo dice con esas palabras en lugar
     * de devolver un error de E/S que nadie sabe interpretar.
     */
    @Override
    public com.officeplatform.dto.response.PathValidationResponse testRemoteReplication() {
        if (!replicationEnabled) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path(replicationDirectoryPath == null ? "" : replicationDirectoryPath)
                    .message("La replicacion externa esta desactivada. Activala para probar el destino.")
                    .build();
        }
        String configured = replicationDirectoryPath == null ? "" : replicationDirectoryPath.trim();
        if (configured.isEmpty()) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path("")
                    .message("No hay un directorio de replicacion configurado.")
                    .build();
        }

        boolean unc = configured.startsWith("\\\\") || configured.startsWith("//");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

        com.officeplatform.dto.response.PathValidationResponse result = validatePath(configured);
        if (result.isValid()) {
            return result;
        }
        if (unc && !windows) {
            return com.officeplatform.dto.response.PathValidationResponse.builder()
                    .valid(false).path(configured)
                    .message("La ruta UNC '" + configured + "' no es alcanzable desde el contenedor. "
                            + "Monta el recurso de red en el host y expone ese punto de montaje como "
                            + "volumen en docker-compose; despues configura aca la ruta del volumen.")
                    .build();
        }
        return result;
    }

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
