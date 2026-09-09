package com.officeplatform.service.version;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FileVersionEntity;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FileVersionRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.util.FileUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileVersionServiceImpl implements FileVersionService {

    /** Prefijo de los objetos históricos, para distinguirlos del contenido vigente. */
    private static final String VERSION_PREFIX = "versions/";

    private final FileVersionRepository fileVersionRepository;
    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final ActivityLogRecorder activityLogRecorder;

    public FileVersionServiceImpl(FileVersionRepository fileVersionRepository,
                                  FileRepository fileRepository,
                                  StorageService storageService,
                                  ActivityLogRecorder activityLogRecorder) {
        this.fileVersionRepository = fileVersionRepository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.activityLogRecorder = activityLogRecorder;
    }

    @Override
    @Transactional
    public FileVersionEntity archiveCurrent(FileEntity file, String userId, String userName, String comment) {
        if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
            return null;
        }
        try {
            byte[] current;
            try (InputStream in = storageService.retrieve(file.getObjectName())) {
                current = in.readAllBytes();
            }
            if (current.length == 0) {
                // Un objeto vacío no es un estado al que alguien quiera volver, y guardarlo
                // llenaría el historial de ruido.
                return null;
            }

            int next = nextVersionNumber(file.getId());
            String extension = FileUtils.extractExtension(file.getOriginalFileName());
            String objectName = VERSION_PREFIX + file.getUuid() + "/v" + next
                    + (extension == null || extension.isBlank() ? "" : "." + extension);

            storageService.store(objectName, new ByteArrayInputStream(current), current.length,
                    file.getMimeType());

            return fileVersionRepository.save(FileVersionEntity.builder()
                    .fileId(file.getId())
                    .versionNumber(next)
                    .objectName(objectName)
                    .size((long) current.length)
                    .createdByUserId(userId)
                    .createdByName(userName)
                    .createdAt(LocalDateTime.now())
                    .comment(comment)
                    .build());
        } catch (Exception e) {
            // Deliberadamente silencioso hacia arriba. Esto corre dentro del guardado del editor:
            // perder una versión es lamentable, perder el trabajo que la persona acaba de guardar
            // es inaceptable.
            log.warn("No se pudo archivar la versión del archivo {}: {}", file.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    public List<FileVersionEntity> listVersions(Long fileId) {
        return fileVersionRepository.findAllByFileIdOrderByVersionNumberDesc(fileId);
    }

    @Override
    @Transactional
    public FileEntity restoreVersion(Long fileId, Integer versionNumber, Long apiKeyId,
                                     String userId, String userName) {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        FileVersionEntity version = fileVersionRepository
                .findByFileIdAndVersionNumber(fileId, versionNumber)
                .orElseThrow(() -> new StorageException(
                        "La versión " + versionNumber + " no existe para este archivo"));

        // El estado vigente se archiva ANTES de pisarlo: restaurar no puede ser una operación que
        // destruya. Una restauración equivocada se deshace restaurando la versión que ella misma
        // acaba de crear.
        archiveCurrent(file, userId, userName, "Estado previo a restaurar la versión " + versionNumber);

        byte[] content;
        try (InputStream in = storageService.retrieve(version.getObjectName())) {
            content = in.readAllBytes();
        } catch (Exception e) {
            throw new StorageException("No se pudo leer la versión " + versionNumber + ": " + e.getMessage());
        }

        storageService.store(file.getObjectName(), new ByteArrayInputStream(content), content.length,
                file.getMimeType());

        file.setSize((long) content.length);
        file.setUpdatedAt(LocalDateTime.now());
        if (userName != null && !userName.isBlank()) {
            file.setUpdatedByName(userName);
        }
        FileEntity saved = fileRepository.save(file);

        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.RESTORE,
                saved.getId(), saved.getOriginalFileName(),
                "Restaurada la versión " + versionNumber + " del historial", saved.getFolderId());
        return saved;
    }

    @Override
    public InputStream downloadVersion(Long fileId, Integer versionNumber) {
        FileVersionEntity version = fileVersionRepository
                .findByFileIdAndVersionNumber(fileId, versionNumber)
                .orElseThrow(() -> new StorageException(
                        "La versión " + versionNumber + " no existe para este archivo"));
        return storageService.retrieve(version.getObjectName());
    }

    @Override
    @Transactional
    public void purgeVersions(Long fileId) {
        List<FileVersionEntity> versions = fileVersionRepository.findAllByFileId(fileId);
        for (FileVersionEntity version : versions) {
            // El binario se borra fuera de la fila: si el objeto ya no está, la fila igual debe
            // irse, o purgar un archivo dejaría historial apuntando a la nada.
            try {
                storageService.delete(version.getObjectName());
            } catch (Exception e) {
                log.warn("No se pudo borrar el binario de la versión {}: {}",
                        version.getObjectName(), e.getMessage());
            }
        }
        fileVersionRepository.deleteAll(versions);
    }

    /** Siguiente correlativo del archivo. Empieza en 1 y nunca reutiliza números. */
    private int nextVersionNumber(Long fileId) {
        return fileVersionRepository.findFirstByFileIdOrderByVersionNumberDesc(fileId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);
    }

}
