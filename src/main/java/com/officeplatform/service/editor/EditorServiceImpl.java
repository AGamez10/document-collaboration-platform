package com.officeplatform.service.editor;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.officeplatform.dto.response.EditorConfigResponse;
import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.EditorSessionEntity;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.exception.OnlyOfficeException;
import com.officeplatform.onlyoffice.dto.OnlyOfficeCallbackRequest;
import com.officeplatform.onlyoffice.dto.OnlyOfficeConfig;
import com.officeplatform.onlyoffice.dto.OnlyOfficeDocument;
import com.officeplatform.onlyoffice.dto.OnlyOfficeEditorConfig;
import com.officeplatform.onlyoffice.dto.OnlyOfficePermissions;
import com.officeplatform.onlyoffice.service.OnlyOfficeService;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.file.FileService;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.util.DateUtils;
import com.officeplatform.util.UrlUtils;

import com.officeplatform.service.share.ShareService;

@Service
public class EditorServiceImpl implements EditorService {

    private static final int SAVE_STATUS = 2;
    private static final int CLOSED_NO_CHANGES_STATUS = 4;
    private static final int FORCE_SAVE_STATUS = 6;
    private static final int MAX_USER_FIELD_LENGTH = 100;

    private final FileService fileService;
    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final OnlyOfficeService onlyOfficeService;
    private final ActivityLogRecorder activityLogRecorder;
    private final EditorSessionRepository editorSessionRepository;
    private final RestTemplate restTemplate;
    private final ShareService shareService;
    private final String documentServerUrl;
    private final String documentServerPublicUrl;
    private final String callbackUrl;
    private final String appInternalUrl;

    public EditorServiceImpl(
            FileService fileService,
            FileRepository fileRepository,
            StorageService storageService,
            OnlyOfficeService onlyOfficeService,
            ActivityLogRecorder activityLogRecorder,
            EditorSessionRepository editorSessionRepository,
            RestTemplate restTemplate,
            ShareService shareService,
            @Value("${office-platform.onlyoffice.document-server-url}") String documentServerUrl,
            @Value("${office-platform.onlyoffice.document-server-public-url}") String documentServerPublicUrl,
            @Value("${office-platform.onlyoffice.callback-url}") String callbackUrl,
            @Value("${office-platform.app-internal-url}") String appInternalUrl) {
        this.fileService = fileService;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.onlyOfficeService = onlyOfficeService;
        this.activityLogRecorder = activityLogRecorder;
        this.editorSessionRepository = editorSessionRepository;
        this.restTemplate = restTemplate;
        this.shareService = shareService;
        this.documentServerUrl = documentServerUrl;
        this.documentServerPublicUrl = documentServerPublicUrl;
        this.callbackUrl = callbackUrl;
        this.appInternalUrl = appInternalUrl;
    }

    @Override
    public EditorConfigResponse getEditorConfig(Long fileId, ApiKeyPrincipal principal, String userId, String userName) {
        FileEntity fileEntity = fileService.getFile(fileId, principal.getApiKeyId());

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, fileId, principal);

        if (level == null) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("No tienes permisos para acceder a este archivo.");
        }

        boolean canEdit = level == com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT;
        boolean canDownload = level == com.officeplatform.entity.SharePermissionEntity.PermissionLevel.DOWNLOAD
                || level == com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT;

        String documentUrl = appInternalUrl.replaceAll("/$", "")
                + "/api/files/download/" + fileEntity.getUuid();

        OnlyOfficePermissions permissions = new OnlyOfficePermissions(
                canEdit,      // edit
                canDownload,  // download
                canDownload,  // print
                canEdit,      // comment
                canEdit,      // fillForms
                canEdit       // review
        );

        String documentKey = buildDocumentKey(fileEntity);
        OnlyOfficeDocument document = new OnlyOfficeDocument(
                fileEntity.getExtension(),
                documentKey,
                fileEntity.getOriginalFileName(),
                documentUrl,
                permissions);

        String resolvedUserId = resolveUserField(userId, String.valueOf(principal.getApiKeyId()));
        String resolvedUserName = resolveUserField(userName, principal.getApiKeyName());
        Map<String, Object> user = Map.of("id", resolvedUserId, "name", resolvedUserName);

        String editorMode = canEdit ? "edit" : "view";
        OnlyOfficeEditorConfig editorConfig = new OnlyOfficeEditorConfig(callbackUrl, "es", editorMode, user);

        OnlyOfficeConfig config = new OnlyOfficeConfig(document, resolveDocumentType(fileEntity.getExtension()), editorConfig, null);
        String token = onlyOfficeService.signConfig(config);
        config.setToken(token);

        editorSessionRepository.save(EditorSessionEntity.builder()
                .fileId(fileEntity.getId())
                .fileName(fileEntity.getOriginalFileName())
                .documentKey(documentKey)
                .userId(resolvedUserId)
                .userName(resolvedUserName)
                .apiKeyId(principal.getApiKeyId())
                .openedAt(LocalDateTime.now())
                .build());
        long activeSessionsCount = editorSessionRepository.findAllByFileIdAndClosedAtIsNull(fileEntity.getId()).size();

        activityLogRecorder.record(principal.getApiKeyId(), userId, userName, ActivityAction.EDITOR_OPEN,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());

        return new EditorConfigResponse(document, documentServerPublicUrl, user, permissions, token, editorConfig, activeSessionsCount);
    }

    @Override
    public void processCallback(OnlyOfficeCallbackRequest callback) {
        onlyOfficeService.validateCallback(callback);

        String updatedByName = null;
        if (isSaveStatus(callback.getStatus()) || isCloseStatus(callback.getStatus())) {
            List<EditorSessionEntity> sessions = editorSessionRepository.findAllByDocumentKeyAndClosedAtIsNull(callback.getKey());
            if (!sessions.isEmpty()) {
                updatedByName = sessions.get(0).getUserName();
            }
        }

        if (isCloseStatus(callback.getStatus())) {
            closeSessionsForKey(callback.getKey());
        }

        if (!isSaveStatus(callback.getStatus())) {
            return;
        }

        String uuid = extractUuidFromKey(callback.getKey());
        FileEntity fileEntity = fileRepository.findByUuid(uuid)
                .orElseThrow(() -> new OnlyOfficeException("El callback de OnlyOffice referencia un archivo inexistente: " + uuid));

        String internalDownloadUrl = UrlUtils.rewriteHost(callback.getUrl(), documentServerUrl);
        byte[] content = restTemplate.getForObject(internalDownloadUrl, byte[].class);
        if (content == null) {
            throw new OnlyOfficeException("No se pudo descargar el documento actualizado desde OnlyOffice");
        }

        storageService.store(
                fileEntity.getObjectName(),
                new ByteArrayInputStream(content),
                content.length,
                fileEntity.getMimeType());

        fileEntity.setSize((long) content.length);
        fileEntity.setUpdatedAt(LocalDateTime.now());
        if (updatedByName != null) {
            fileEntity.setUpdatedByName(updatedByName);
        }
        fileRepository.save(fileEntity);

        List<String> users = callback.getUsers();
        String userId = (users != null && !users.isEmpty()) ? users.get(0) : null;
        activityLogRecorder.record(fileEntity.getApiKeyId(), userId, updatedByName, ActivityAction.EDITOR_SAVE,
                fileEntity.getId(), fileEntity.getOriginalFileName(), "users: " + users, fileEntity.getFolderId());
    }

    @Override
    @Transactional
    public void closeSession(Long fileId, String documentKey, Long apiKeyId) {
        List<EditorSessionEntity> sessions = editorSessionRepository.findAllByDocumentKeyAndClosedAtIsNull(documentKey)
                .stream()
                .filter(session -> session.getFileId().equals(fileId) && session.getApiKeyId().equals(apiKeyId))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        sessions.forEach(session -> session.setClosedAt(now));
        editorSessionRepository.saveAll(sessions);
    }

    // Not @Transactional: this is a same-class call from processCallback (no Spring proxy
    // involved), and saveAll() already runs its own transaction via Spring Data JPA.
    private void closeSessionsForKey(String documentKey) {
        List<EditorSessionEntity> sessions = editorSessionRepository.findAllByDocumentKeyAndClosedAtIsNull(documentKey);
        LocalDateTime now = LocalDateTime.now();
        sessions.forEach(session -> session.setClosedAt(now));
        editorSessionRepository.saveAll(sessions);
    }

    private boolean isSaveStatus(Integer status) {
        return status != null && (status == SAVE_STATUS || status == FORCE_SAVE_STATUS);
    }

    private boolean isCloseStatus(Integer status) {
        return status != null && (status == SAVE_STATUS || status == CLOSED_NO_CHANGES_STATUS);
    }

    private String resolveUserField(String provided, String fallback) {
        if (provided == null || provided.isBlank()) {
            return fallback;
        }
        String trimmed = provided.trim();
        return trimmed.length() > MAX_USER_FIELD_LENGTH ? trimmed.substring(0, MAX_USER_FIELD_LENGTH) : trimmed;
    }

    private String buildDocumentKey(FileEntity fileEntity) {
        long version = fileEntity.getUpdatedAt() != null
                ? DateUtils.toEpochSeconds(fileEntity.getUpdatedAt())
                : DateUtils.toEpochSeconds(fileEntity.getCreatedAt());
        return "file_" + fileEntity.getUuid() + "_" + version;
    }

    private String extractUuidFromKey(String key) {
        if (key == null) {
            throw new OnlyOfficeException("El callback de OnlyOffice no incluye key");
        }
        int firstUnderscore = key.indexOf('_');
        int lastUnderscore = key.lastIndexOf('_');
        if (firstUnderscore < 0 || lastUnderscore <= firstUnderscore) {
            throw new OnlyOfficeException("El key del callback de OnlyOffice tiene un formato inválido: " + key);
        }
        return key.substring(firstUnderscore + 1, lastUnderscore);
    }

    private String resolveDocumentType(String extension) {
        if (extension == null) {
            return "word";
        }
        return switch (extension.toLowerCase()) {
            case "xls", "xlsx", "ods", "csv" -> "cell";
            case "ppt", "pptx", "odp" -> "slide";
            default -> "word";
        };
    }

}
