package com.officeplatform.service.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.officeplatform.dto.request.UploadFileRequest;
import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.exception.FolderNotFoundException;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.util.DocxUtils;
import com.officeplatform.util.FileUtils;
import com.officeplatform.util.MimeUtils;
import com.officeplatform.util.PptxUtils;
import com.officeplatform.util.XlsxUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    private static final String BLANK_DOCUMENT_NAME = "Nuevo documento.docx";
    private static final String BLANK_DOCUMENT_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String BLANK_DOCUMENT_EXTENSION = "docx";

    private static final String BLANK_SPREADSHEET_NAME = "Nueva hoja de cálculo.xlsx";
    private static final String BLANK_SPREADSHEET_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String BLANK_SPREADSHEET_EXTENSION = "xlsx";

    private static final String BLANK_PRESENTATION_NAME = "Nueva presentación.pptx";
    private static final String BLANK_PRESENTATION_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String BLANK_PRESENTATION_EXTENSION = "pptx";

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final StorageService storageService;
    private final ActivityLogRecorder activityLogRecorder;
    private final String bucket;
    private final List<String> allowedMimeTypes;

    public FileServiceImpl(
            FileRepository fileRepository,
            FolderRepository folderRepository,
            StorageService storageService,
            ActivityLogRecorder activityLogRecorder,
            @Value("${office-platform.storage.minio.bucket}") String bucket,
            @Value("${office-platform.storage.allowed-mime-types}") String allowedMimeTypesRaw) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.storageService = storageService;
        this.activityLogRecorder = activityLogRecorder;
        this.bucket = bucket;
        this.allowedMimeTypes = Arrays.asList(allowedMimeTypesRaw.split(","));
    }

    private String resolveEffectiveUserId(String userId, Long apiKeyId) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        return "key_" + apiKeyId;
    }

    @Override
    public List<FileEntity> listFiles(Long apiKeyId, Long folderId, String userId, String scope) {
        log.info("[FILE-SVC] listFiles scope={}, userId={}, apiKeyId={}, folderId={}",
                scope, userId, apiKeyId, folderId);
        if (folderId != null) {
            return fileRepository.findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(apiKeyId, folderId);
        }
        if ("shared".equalsIgnoreCase(scope)) {
            return fileRepository.findAllByApiKeyIdAndFolderIdIsNullAndUserIdIsNullAndDeletedAtIsNull(apiKeyId);
        }
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return fileRepository.findAllByApiKeyIdAndFolderIdIsNullAndUserIdAndDeletedAtIsNull(apiKeyId, userId.trim());
    }

    @Override
    public List<FileEntity> listAllFiles(Long apiKeyId, String userId, String scope) {
        if ("shared".equalsIgnoreCase(scope)) {
            return fileRepository.findAllByApiKeyIdAndUserIdIsNullAndDeletedAtIsNull(apiKeyId);
        }
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return fileRepository.findAllByApiKeyIdAndUserIdAndDeletedAtIsNull(apiKeyId, userId.trim());
    }

    /**
     * Trash of the calling user, resolved by ownership instead of by scope.
     *
     * <p>Scope-based filtering broke this listing: a shared file is stored with
     * {@code user_id = null}, so once its author trashed it, the private trash
     * ({@code user_id = cédula}) no longer matched it while the shared trash
     * ({@code user_id IS NULL}) showed it to the whole project. Ownership is the correct
     * criterion — a trashed file belongs in its author's trash and nobody else's.
     *
     * <p>{@code createdByUserId} is the strong match. Rows created before that column existed
     * carry only {@code createdByName}, so those are matched by display name, and legacy private
     * rows are still matched by {@code user_id} to avoid losing anything already in the trash.
     * The {@code scope} parameter is kept for API compatibility and no longer filters.
     */
    @Override
    public List<FileEntity> listTrash(Long apiKeyId, String userId, String scope) {
        return listTrash(apiKeyId, userId, null, scope);
    }

    @Override
    public List<FileEntity> listTrash(Long apiKeyId, String userId, String userName, String scope) {
        String resolvedUserId = (userId != null && !userId.isBlank()) ? userId.trim() : null;
        String resolvedUserName = (userName != null && !userName.isBlank()) ? userName.trim() : null;

        if (resolvedUserId == null && resolvedUserName == null) {
            return List.of();
        }

        Map<Long, FileEntity> byId = new LinkedHashMap<>();

        if (resolvedUserId != null) {
            fileRepository.findAllByApiKeyIdAndCreatedByUserIdAndDeletedAtIsNotNull(apiKeyId, resolvedUserId)
                    .forEach(file -> byId.put(file.getId(), file));
            // Legacy private rows: owned through user_id, created before created_by_user_id existed.
            fileRepository.findAllByApiKeyIdAndUserIdAndDeletedAtIsNotNull(apiKeyId, resolvedUserId)
                    .forEach(file -> byId.putIfAbsent(file.getId(), file));
        }

        if (resolvedUserName != null) {
            fileRepository
                    .findAllByApiKeyIdAndCreatedByUserIdIsNullAndCreatedByNameAndDeletedAtIsNotNull(
                            apiKeyId, resolvedUserName)
                    .forEach(file -> byId.putIfAbsent(file.getId(), file));
        }

        return byId.values().stream()
                .sorted(Comparator.comparing(
                        FileEntity::getDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public List<FileEntity> searchFiles(Long apiKeyId, String term, String userId, String scope) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String query = term.trim();
        if ("shared".equalsIgnoreCase(scope)) {
            return fileRepository
                    .findAllByApiKeyIdAndUserIdIsNullAndOriginalFileNameContainingIgnoreCaseAndDeletedAtIsNull(
                            apiKeyId, query);
        }
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return fileRepository
                .findAllByApiKeyIdAndUserIdAndOriginalFileNameContainingIgnoreCaseAndDeletedAtIsNull(
                        apiKeyId, userId.trim(), query);
    }

    @Override
    public FileEntity uploadFile(
            MultipartFile file, UploadFileRequest request, Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("El archivo está vacío o no fue enviado");
        }

        String contentType = file.getContentType();
        if (!MimeUtils.isAllowed(contentType, allowedMimeTypes)) {
            throw new StorageException("Tipo de archivo no permitido: " + contentType);
        }

        String uuid = UUID.randomUUID().toString();
        String extension = FileUtils.extractExtension(request.getOriginalFileName());
        String storedFileName = FileUtils.generateStoredFileName(uuid, extension);

        try (InputStream inputStream = file.getInputStream()) {
            storageService.store(storedFileName, inputStream, file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("No se pudo leer el archivo subido", e);
        }

        String fileUserId = null;
        if (folderId != null) {
            FolderEntity parent = folderRepository.findByIdAndApiKeyId(folderId, apiKeyId)
                    .orElseThrow(() -> new FolderNotFoundException(folderId));
            fileUserId = parent.getUserId();
        } else {
            if ("private".equalsIgnoreCase(scope)) {
                if (userId == null || userId.isBlank()) {
                    throw new StorageException("Se requiere una identidad de usuario autenticado para subir archivos a Mis Archivos.");
                }
                fileUserId = userId.trim();
            }
        }

        String effectiveUserName = (userName != null && !userName.isBlank()) ? userName : "Usuario";

        FileEntity fileEntity = FileEntity.builder()
            .uuid(uuid)
            .fileName(storedFileName)
            .originalFileName(request.getOriginalFileName())
            .bucket(bucket)
            .objectName(storedFileName)
            .mimeType(contentType)
            .extension(extension)
            .size(file.getSize())
            .apiKeyId(apiKeyId)
            .folderId(folderId)
            .userId(fileUserId)
            .createdByName(effectiveUserName)
            .createdByUserId(userId)
            .updatedByName(effectiveUserName)
            .build();

        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, effectiveUserName, ActivityAction.UPLOAD,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
        return saved;
    }

    @Override
    public FileEntity createBlankFile(Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        return createBlank(DocxUtils.emptyDocument(), BLANK_DOCUMENT_NAME, BLANK_DOCUMENT_MIME_TYPE,
                BLANK_DOCUMENT_EXTENSION, apiKeyId, folderId, userId, userName, scope);
    }

    @Override
    public FileEntity createBlankSpreadsheet(Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        return createBlank(XlsxUtils.emptySpreadsheet(), BLANK_SPREADSHEET_NAME, BLANK_SPREADSHEET_MIME_TYPE,
                BLANK_SPREADSHEET_EXTENSION, apiKeyId, folderId, userId, userName, scope);
    }

    @Override
    public FileEntity createBlankPresentation(Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        return createBlank(PptxUtils.emptyPresentation(), BLANK_PRESENTATION_NAME, BLANK_PRESENTATION_MIME_TYPE,
                BLANK_PRESENTATION_EXTENSION, apiKeyId, folderId, userId, userName, scope);
    }

    private FileEntity createBlank(
            byte[] content, String name, String mimeType, String extension,
            Long apiKeyId, Long folderId, String userId, String userName, String scope) {
        String uuid = UUID.randomUUID().toString();
        String storedFileName = FileUtils.generateStoredFileName(uuid, extension);

        storageService.store(
                storedFileName,
                new ByteArrayInputStream(content),
                content.length,
                mimeType);

        String fileUserId = null;
        if (folderId != null) {
            FolderEntity parent = folderRepository.findByIdAndApiKeyId(folderId, apiKeyId)
                    .orElseThrow(() -> new FolderNotFoundException(folderId));
            fileUserId = parent.getUserId();
        } else {
            if ("private".equalsIgnoreCase(scope)) {
                if (userId == null || userId.isBlank()) {
                    throw new StorageException("Se requiere una identidad de usuario autenticado para crear archivos en Mis Archivos.");
                }
                fileUserId = userId.trim();
            }
        }

        String effectiveUserName = (userName != null && !userName.isBlank()) ? userName : "Usuario";

        FileEntity fileEntity = FileEntity.builder()
            .uuid(uuid)
            .fileName(storedFileName)
            .originalFileName(name)
            .bucket(bucket)
            .objectName(storedFileName)
            .mimeType(mimeType)
            .extension(extension)
            .size((long) content.length)
            .apiKeyId(apiKeyId)
            .folderId(folderId)
            .userId(fileUserId)
            .createdByName(effectiveUserName)
            .createdByUserId(userId)
            .updatedByName(effectiveUserName)
            .build();

        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, effectiveUserName, ActivityAction.CREATE_BLANK,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
        return saved;
    }

    @Override
    public FileEntity getFile(Long fileId, Long apiKeyId) {
        return fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNull(fileId, apiKeyId)
            .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    @Override
    public FileEntity getFileIncludingTrashed(Long fileId, Long apiKeyId) {
        return fileRepository.findByIdAndApiKeyId(fileId, apiKeyId)
            .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    @Override
    @Transactional
    public FileEntity renameFile(Long fileId, Long apiKeyId, String newName, String userId, String userName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new StorageException("El nombre del archivo no puede estar vacío");
        }

        String trimmedName = newName.trim();
        if (trimmedName.length() > MAX_FILE_NAME_LENGTH) {
            throw new StorageException(
                    "El nombre del archivo no puede exceder " + MAX_FILE_NAME_LENGTH + " caracteres");
        }

        FileEntity fileEntity = getFile(fileId, apiKeyId);
        String oldName = fileEntity.getOriginalFileName();
        fileEntity.setOriginalFileName(trimmedName);
        fileEntity.setUpdatedByName(userName);

        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.RENAME,
                saved.getId(), saved.getOriginalFileName(),
                "Renombrado de '" + oldName + "' a '" + trimmedName + "'", saved.getFolderId());
        return saved;
    }

    @Override
    @Transactional
    public void softDeleteFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = getFile(fileId, apiKeyId);
        fileEntity.setDeletedAt(LocalDateTime.now());
        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.DELETE,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
    }

    @Override
    @Transactional
    public FileEntity restoreFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNotNull(fileId, apiKeyId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        fileEntity.setDeletedAt(null);
        FileEntity saved = fileRepository.save(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.RESTORE,
                saved.getId(), saved.getOriginalFileName(), null, saved.getFolderId());
        return saved;
    }

    @Override
    @Transactional
    public void purgeFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = fileRepository.findByIdAndApiKeyIdAndDeletedAtIsNotNull(fileId, apiKeyId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        storageService.delete(fileEntity.getObjectName());
        fileRepository.delete(fileEntity);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.PURGE,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());
    }

    @Override
    public InputStream downloadFile(Long fileId, Long apiKeyId, String userId, String userName) {
        FileEntity fileEntity = getFile(fileId, apiKeyId);
        activityLogRecorder.record(apiKeyId, userId, userName, ActivityAction.DOWNLOAD,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());
        return storageService.retrieve(fileEntity.getObjectName());
    }

    @Override
    public FileEntity getFileByUuid(String uuid) {
        return fileRepository.findByUuid(uuid)
                .orElseThrow(() -> new FileNotFoundException(-1L));
    }

    @Override
    public InputStream downloadByUuid(String uuid) {
        FileEntity fileEntity = getFileByUuid(uuid);
        activityLogRecorder.record(fileEntity.getApiKeyId(), null, null, ActivityAction.DOWNLOAD,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());
        return storageService.retrieve(fileEntity.getObjectName());
    }

}
