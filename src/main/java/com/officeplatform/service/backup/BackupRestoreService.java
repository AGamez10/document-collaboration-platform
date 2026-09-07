package com.officeplatform.service.backup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Reinstates the contents of a backup package into the database and object storage.
 *
 * <p>Kept separate from {@link BackupServiceImpl} because restoring is the inverse operation and
 * carries the opposite risk profile: it writes across four tables that reference each other, so it
 * needs its own transaction boundary and its own reasoning about identity.
 *
 * <p><b>Rows are matched by business key, never by numeric id.</b> A backup taken on one instance
 * carries the ids that instance had assigned; replaying them on a database that has kept working
 * would either collide with unrelated rows or resurrect them under the wrong parent. API keys are
 * matched by their key string, known users by (project, cédula), folders and files by uuid. The
 * numeric ids from the package are used only to rebuild relationships, through a map from the id
 * recorded in the backup to the id this database actually holds.
 *
 * <p>That is also why the order matters: {@code api_keys → known_users → folders → files}. Each
 * step needs the previous step's mapping to resolve its own foreign keys, and folders are written
 * in two passes so a child can point at a parent that appears later in the manifest.
 */
@Service
@Slf4j
public class BackupRestoreService {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String FILES_PREFIX = "files/";

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final KnownUserRepository knownUserRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final String bucket;

    public BackupRestoreService(
            FileRepository fileRepository,
            FolderRepository folderRepository,
            ApiKeyRepository apiKeyRepository,
            KnownUserRepository knownUserRepository,
            StorageService storageService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${office-platform.storage.minio.bucket}") String bucket) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.knownUserRepository = knownUserRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        // An explicit template rather than @Transactional on a method of this same bean: that
        // annotation is applied by a proxy, and a call from inside the class bypasses it, so the
        // metadata would have been written with no transaction at all.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.bucket = bucket;
    }

    /** Accumulates counters and warnings while the restore walks the package. */
    private static final class Tally {
        int apiKeysCreated, apiKeysUpdated;
        int knownUsersCreated, knownUsersUpdated;
        int foldersCreated, foldersUpdated;
        int filesCreated, filesUpdated;
        int binariesRestored, binariesSkipped;
        final List<String> warnings = new ArrayList<>();
    }

    /**
     * Restores a backup package read from disk.
     *
     * @param zipPath  location of the .zip package
     * @param fileName name reported back to the caller
     */
    public BackupRestoreResponse restore(Path zipPath, String fileName) {
        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new StorageException("La copia de seguridad no existe: " + fileName);
        }
        try (InputStream in = Files.newInputStream(zipPath)) {
            return restore(in, fileName);
        } catch (IOException e) {
            throw new StorageException("No se pudo leer la copia de seguridad: " + e.getMessage());
        }
    }

    /**
     * Restores a backup package read from a stream, so an externally supplied upload follows
     * exactly the same path as one already stored on disk.
     *
     * <p>The package is read once into memory-backed buffers because a ZIP has to be traversed
     * twice: the manifest drives the database work, and the binaries can only be pushed to object
     * storage after their file rows exist.
     */
    public BackupRestoreResponse restore(InputStream zipStream, String fileName) {
        JsonNode manifest = null;
        Map<String, byte[]> binaries = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (MANIFEST_ENTRY.equals(name)) {
                    manifest = objectMapper.readTree(readEntry(zis));
                } else if (name.startsWith(FILES_PREFIX)) {
                    binaries.put(name.substring(FILES_PREFIX.length()), readEntry(zis));
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("El paquete de respaldo está corrupto o no es un ZIP válido: "
                    + e.getMessage());
        }

        if (manifest == null) {
            throw new IllegalArgumentException(
                    "El paquete no contiene manifest.json; no parece una copia de seguridad de Office Platform.");
        }

        Tally tally = new Tally();
        restoreMetadata(manifest, tally);
        restoreBinaries(binaries, tally);

        log.info("Restauración de '{}' completada: {} archivos, {} carpetas, {} binarios ({} omitidos)",
                fileName, tally.filesCreated + tally.filesUpdated,
                tally.foldersCreated + tally.foldersUpdated, tally.binariesRestored, tally.binariesSkipped);

        return BackupRestoreResponse.builder()
                .fileName(fileName)
                .restoredAt(LocalDateTime.now())
                .apiKeysCreated(tally.apiKeysCreated)
                .apiKeysUpdated(tally.apiKeysUpdated)
                .knownUsersCreated(tally.knownUsersCreated)
                .knownUsersUpdated(tally.knownUsersUpdated)
                .foldersCreated(tally.foldersCreated)
                .foldersUpdated(tally.foldersUpdated)
                .filesCreated(tally.filesCreated)
                .filesUpdated(tally.filesUpdated)
                .binariesRestored(tally.binariesRestored)
                .binariesSkipped(tally.binariesSkipped)
                .warnings(tally.warnings)
                .build();
    }

    /**
     * Writes the manifest into the database in dependency order, inside a single transaction so a
     * failure half-way cannot leave folders pointing at parents that were never committed.
     */
    private void restoreMetadata(JsonNode manifest, Tally tally) {
        transactionTemplate.executeWithoutResult(status -> {
            Map<Long, Long> apiKeyIds = restoreApiKeys(manifest.path("apiKeysMetadata"), tally);
            restoreKnownUsers(manifest.path("knownUsersMetadata"), apiKeyIds, tally);
            Map<Long, Long> folderIds = restoreFolders(manifest.path("foldersMetadata"), apiKeyIds, tally);
            restoreFiles(manifest.path("filesMetadata"), apiKeyIds, folderIds, tally);
        });
    }

    /** @return map from the api key id recorded in the backup to the id in this database */
    private Map<Long, Long> restoreApiKeys(JsonNode nodes, Tally tally) {
        Map<Long, Long> idMap = new HashMap<>();
        for (JsonNode node : arrayOf(nodes)) {
            String key = text(node, "apiKey");
            if (key == null) {
                continue;
            }
            ApiKeyEntity entity = apiKeyRepository.findByApiKey(key).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new ApiKeyEntity();
                entity.setApiKey(key);
                entity.setCreatedAt(dateTime(node, "createdAt", LocalDateTime.now()));
            }
            entity.setName(text(node, "name"));
            entity.setActive(bool(node, "active", Boolean.TRUE));

            ApiKeyEntity saved = apiKeyRepository.save(entity);
            Long backupId = number(node, "id");
            if (backupId != null) {
                idMap.put(backupId, saved.getId());
            }
            if (isNew) {
                tally.apiKeysCreated++;
            } else {
                tally.apiKeysUpdated++;
            }
        }
        return idMap;
    }

    private void restoreKnownUsers(JsonNode nodes, Map<Long, Long> apiKeyIds, Tally tally) {
        for (JsonNode node : arrayOf(nodes)) {
            Long apiKeyId = mapped(apiKeyIds, number(node, "apiKeyId"));
            String userId = text(node, "userId");
            if (apiKeyId == null || userId == null || userId.isBlank()) {
                tally.warnings.add("Usuario conocido omitido: no se pudo resolver su proyecto (" + userId + ")");
                continue;
            }
            KnownUserEntity entity = knownUserRepository
                    .findByApiKeyIdAndUserId(apiKeyId, userId.trim()).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new KnownUserEntity();
                entity.setApiKeyId(apiKeyId);
                entity.setUserId(userId.trim());
                entity.setFirstSeenAt(dateTime(node, "firstSeenAt", LocalDateTime.now()));
            }
            entity.setDisplayName(text(node, "displayName"));
            entity.setRole(textOr(node, "role", "user"));
            entity.setLastSeenAt(dateTime(node, "lastSeenAt", LocalDateTime.now()));

            knownUserRepository.save(entity);
            if (isNew) {
                tally.knownUsersCreated++;
            } else {
                tally.knownUsersUpdated++;
            }
        }
    }

    /**
     * Folders are written in two passes. The first creates or refreshes every folder without its
     * parent, which builds the complete id mapping; the second wires the hierarchy. A single pass
     * would fail whenever a child appears in the manifest before its parent.
     *
     * @return map from the folder id recorded in the backup to the id in this database
     */
    private Map<Long, Long> restoreFolders(JsonNode nodes, Map<Long, Long> apiKeyIds, Tally tally) {
        Map<Long, Long> idMap = new HashMap<>();
        List<JsonNode> pending = new ArrayList<>();

        for (JsonNode node : arrayOf(nodes)) {
            String uuid = text(node, "uuid");
            if (uuid == null) {
                continue;
            }
            FolderEntity entity = folderRepository.findByUuid(uuid).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new FolderEntity();
                entity.setUuid(uuid);
                entity.setCreatedAt(dateTime(node, "createdAt", LocalDateTime.now()));
            }
            entity.setName(text(node, "name"));
            entity.setUpdatedAt(dateTime(node, "updatedAt", LocalDateTime.now()));
            entity.setApiKeyId(mapped(apiKeyIds, number(node, "apiKeyId")));
            entity.setUserId(text(node, "userId"));
            entity.setCreatedByName(text(node, "createdByName"));
            entity.setCreatedByUserId(text(node, "createdByUserId"));
            entity.setUpdatedByName(text(node, "updatedByName"));

            FolderEntity saved = folderRepository.save(entity);
            Long backupId = number(node, "id");
            if (backupId != null) {
                idMap.put(backupId, saved.getId());
            }
            if (number(node, "parentId") != null) {
                pending.add(node);
            }
            if (isNew) {
                tally.foldersCreated++;
            } else {
                tally.foldersUpdated++;
            }
        }

        for (JsonNode node : pending) {
            String uuid = text(node, "uuid");
            Long parentId = mapped(idMap, number(node, "parentId"));
            if (uuid == null) {
                continue;
            }
            if (parentId == null) {
                tally.warnings.add("Carpeta '" + text(node, "name")
                        + "' restaurada en la raíz: su carpeta padre no estaba en el respaldo.");
                continue;
            }
            folderRepository.findByUuid(uuid).ifPresent(folder -> {
                folder.setParentId(parentId);
                folderRepository.save(folder);
            });
        }
        return idMap;
    }

    private void restoreFiles(JsonNode nodes, Map<Long, Long> apiKeyIds,
                              Map<Long, Long> folderIds, Tally tally) {
        for (JsonNode node : arrayOf(nodes)) {
            String uuid = text(node, "uuid");
            if (uuid == null) {
                continue;
            }
            FileEntity entity = fileRepository.findByUuid(uuid).orElse(null);
            boolean isNew = entity == null;
            if (isNew) {
                entity = new FileEntity();
                entity.setUuid(uuid);
                entity.setCreatedAt(dateTime(node, "createdAt", LocalDateTime.now()));
            }
            entity.setOriginalFileName(text(node, "originalFileName"));
            entity.setObjectName(text(node, "objectName"));
            // NOT NULL columns. The manifest normally carries them; the fallbacks cover a package
            // written by a version that did not record the field yet, so an older backup still
            // restores instead of failing on a constraint.
            entity.setFileName(textOr(node, "fileName", text(node, "objectName")));
            entity.setBucket(textOr(node, "bucket", bucket));
            entity.setUpdatedAt(dateTime(node, "updatedAt", LocalDateTime.now()));
            entity.setMimeType(text(node, "mimeType"));
            entity.setSize(number(node, "size"));
            entity.setApiKeyId(mapped(apiKeyIds, number(node, "apiKeyId")));
            entity.setUserId(text(node, "userId"));
            entity.setCreatedByName(text(node, "createdByName"));
            entity.setCreatedByUserId(text(node, "createdByUserId"));
            entity.setUpdatedByName(text(node, "updatedByName"));
            entity.setDeletedAt(dateTime(node, "deletedAt", null));

            Long backupFolderId = number(node, "folderId");
            Long resolvedFolder = mapped(folderIds, backupFolderId);
            if (backupFolderId != null && resolvedFolder == null) {
                // Its folder is not part of this package; leaving the stale reference would make
                // the file invisible, so it lands at the root of its space instead.
                tally.warnings.add("Archivo '" + text(node, "originalFileName")
                        + "' restaurado en la raíz: su carpeta no estaba en el respaldo.");
            }
            entity.setFolderId(resolvedFolder);

            fileRepository.save(entity);
            if (isNew) {
                tally.filesCreated++;
            } else {
                tally.filesUpdated++;
            }
        }
    }

    /**
     * Pushes the binaries back into object storage.
     *
     * <p>Runs outside the database transaction on purpose: object storage is not transactional, so
     * a failure here must not roll back metadata that is otherwise correct. Each failure is
     * recorded as a warning and the restore continues with the remaining files.
     */
    private void restoreBinaries(Map<String, byte[]> binaries, Tally tally) {
        if (binaries.isEmpty()) {
            return;
        }
        // Resolved once: looking the mime type up per binary would re-read the whole table on
        // every iteration, which on a real package means thousands of full scans.
        Map<String, String> mimeByObjectName = new HashMap<>();
        for (FileEntity file : fileRepository.findAll()) {
            if (file.getObjectName() != null) {
                mimeByObjectName.putIfAbsent(file.getObjectName(), file.getMimeType());
            }
        }

        for (Map.Entry<String, byte[]> binary : binaries.entrySet()) {
            String objectName = binary.getKey();
            byte[] content = binary.getValue();
            String mimeType = mimeByObjectName.getOrDefault(objectName, "application/octet-stream");
            try (InputStream in = new ByteArrayInputStream(content)) {
                storageService.store(objectName, in, content.length, mimeType);
                tally.binariesRestored++;
            } catch (Exception e) {
                tally.binariesSkipped++;
                tally.warnings.add("No se pudo restaurar el binario '" + objectName + "': " + e.getMessage());
                log.warn("No se pudo restaurar el binario {}: {}", objectName, e.getMessage());
            }
        }
    }

    // ── manifest helpers: tolerate a package written by an older version ──────────

    private static byte[] readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        zis.transferTo(out);
        return out.toByteArray();
    }

    private static Iterable<JsonNode> arrayOf(JsonNode node) {
        return (node != null && node.isArray()) ? node : List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull() || !value.canConvertToLong()) ? null : value.asLong();
    }

    private static Boolean bool(JsonNode node, String field, Boolean fallback) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? fallback : value.asBoolean();
    }

    private static LocalDateTime dateTime(JsonNode node, String field, LocalDateTime fallback) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Translates an id recorded in the backup into the id this database holds. */
    private static Long mapped(Map<Long, Long> idMap, Long backupId) {
        return backupId == null ? null : idMap.get(backupId);
    }

}
