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
import com.officeplatform.onlyoffice.dto.OnlyOfficeCustomization;
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
@lombok.extern.slf4j.Slf4j
public class EditorServiceImpl implements EditorService {

    private static final int SAVE_STATUS = 2;
    private static final int CLOSED_NO_CHANGES_STATUS = 4;
    private static final int FORCE_SAVE_STATUS = 6;
    private static final int MAX_USER_FIELD_LENGTH = 100;

    /**
     * Used when no public URL for Document Server is configured.
     *
     * <p>A blank value used to reach the browser unchanged, and the widget then built a relative
     * {@code /web-apps/...} path that resolved against the consuming application's own host —
     * a 404 on every attempt to open a document. Any value here is better than an empty one:
     * a wrong host is visible and fixable, an empty one silently targets the wrong server.
     */
    private static final String DEFAULT_DOCUMENT_SERVER_PUBLIC_URL = "http://localhost:8081";

    private final FileService fileService;
    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final OnlyOfficeService onlyOfficeService;
    private final ActivityLogRecorder activityLogRecorder;
    private final EditorSessionRepository editorSessionRepository;
    private final RestTemplate restTemplate;
    private final ShareService shareService;
    private final com.officeplatform.service.notification.NotificationService notificationService;
    private final com.officeplatform.repository.SharePermissionRepository sharePermissionRepository;
    private final com.officeplatform.repository.KnownUserRepository knownUserRepository;
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
            com.officeplatform.service.notification.NotificationService notificationService,
            com.officeplatform.repository.SharePermissionRepository sharePermissionRepository,
            com.officeplatform.repository.KnownUserRepository knownUserRepository,
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
        this.notificationService = notificationService;
        this.sharePermissionRepository = sharePermissionRepository;
        this.knownUserRepository = knownUserRepository;
        this.documentServerUrl = documentServerUrl;
        // Sanitised at construction so every consumer of this field gets a usable URL. The
        // property has a default, but Docker Compose exports the variable as an empty string,
        // which counts as set and therefore bypasses that default.
        this.documentServerPublicUrl = normalizePublicUrl(documentServerPublicUrl);
        this.callbackUrl = callbackUrl;
        this.appInternalUrl = appInternalUrl;
    }

    /** Trims the configured URL and substitutes the fallback when it is missing or blank. */
    private static String normalizePublicUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DOCUMENT_SERVER_PUBLIC_URL;
        }
        String trimmed = configured.trim();
        return trimmed.replaceAll("/+$", "");
    }

    @Override
    public EditorConfigResponse getEditorConfig(Long fileId, ApiKeyPrincipal principal, String userId, String userName) {
        // Identity is passed so a private file this user owns opens from any consumer
        // application ("Mis archivos" is decentralized). Shared files stay project-scoped.
        FileEntity fileEntity = fileService.getFile(fileId, principal.getApiKeyId(), userId);

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
        OnlyOfficeEditorConfig editorConfig = new OnlyOfficeEditorConfig(callbackUrl, "es", editorMode, user, null);

        // Set before signing on purpose: Document Server only honours this section when it arrives
        // inside the JWT payload, so it has to be part of the object handed to signConfig below.
        editorConfig.setCustomization(OnlyOfficeCustomization.builder()
                .macros(true)
                .macrosMode("enable")
                .plugins(true)
                .autosave(true)
                .forcesave(true)
                .comments(true)
                .build());

        OnlyOfficeConfig config = new OnlyOfficeConfig(document, resolveDocumentType(fileEntity.getExtension()), editorConfig, null);
        String token = onlyOfficeService.signConfig(config);
        config.setToken(token);

        LocalDateTime openedAt = LocalDateTime.now();
        EditorSessionEntity savedSession = editorSessionRepository.save(EditorSessionEntity.builder()
                .fileId(fileEntity.getId())
                .fileName(fileEntity.getOriginalFileName())
                .documentKey(documentKey)
                .userId(resolvedUserId)
                .userName(resolvedUserName)
                .apiKeyId(principal.getApiKeyId())
                .openedAt(openedAt)
                // Seeded with openedAt so a session counts as fresh from the moment it opens,
                // instead of looking stale until the first heartbeat arrives two minutes later.
                .lastHeartbeatAt(openedAt)
                .build());
        long activeSessionsCount = editorSessionRepository.findAllByFileIdAndClosedAtIsNull(fileEntity.getId()).size();

        activityLogRecorder.record(principal.getApiKeyId(), userId, userName, ActivityAction.EDITOR_OPEN,
                fileEntity.getId(), fileEntity.getOriginalFileName(), null, fileEntity.getFolderId());

        // Se avisa al autor cuando otra persona abre su documento. El servicio ignora el caso
        // de abrir lo propio, así que acá no hace falta repetir esa condición.
        // Envuelto en try/catch además del REQUIRES_NEW del servicio: notificar es información
        // secundaria y jamás debe impedir que alguien abra un documento.
        try {
            notificationService.notifyResourceOpened(
                    resolveFileOwner(fileEntity),
                    resolvedUserId,
                    resolvedUserName,
                    fileEntity.getId(),
                    "FILE",
                    fileEntity.getOriginalFileName());
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar la apertura del archivo {}: {}",
                    fileEntity.getId(), e.getMessage());
        }

        return new EditorConfigResponse(document, documentServerPublicUrl, user, permissions, token,
                editorConfig, activeSessionsCount, savedSession.getId());
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

        // Guardar cambios en un documento ajeno es más relevante que abrirlo, así que también
        // se avisa. Secundario como siempre: nunca debe hacer fallar el guardado en sí.
        try {
            String editorName = (updatedByName != null && !updatedByName.isBlank())
                    ? updatedByName : userId;
            notificationService.notifyResourceAction(
                    resolveFileOwner(fileEntity),
                    userId,
                    editorName,
                    fileEntity.getId(),
                    "FILE",
                    "Documento editado",
                    editorName + " editó y guardó cambios en tu documento '"
                            + fileEntity.getOriginalFileName() + "'");
        } catch (RuntimeException e) {
            log.warn("No se pudo notificar la edición del archivo {}: {}",
                    fileEntity.getId(), e.getMessage());
        }
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
            // Los formatos con macros abren con el mismo editor que su equivalente sin macros:
            // el Document Server distingue por documentType, no por la presencia del proyecto VBA.
            case "xls", "xlsx", "xlsm", "xltm", "xlsb", "ods", "csv" -> "cell";
            case "ppt", "pptx", "pptm", "potm", "odp" -> "slide";
            case "doc", "docx", "docm", "dotm", "odt" -> "word";
            default -> "word";
        };
    }


    @Override
    @Transactional
    public boolean heartbeat(Long sessionId, ApiKeyPrincipal principal) {
        EditorSessionEntity session = editorSessionRepository
                .findByIdAndClosedAtIsNull(sessionId).orElse(null);
        if (session == null) {
            // Already reaped or never existed. Answering false rather than throwing lets the
            // widget stop its timer quietly instead of surfacing an error to the user.
            return false;
        }
        // Scoped to the caller's project so one tenant cannot keep another tenant's session alive.
        if (!session.getApiKeyId().equals(principal.getApiKeyId())) {
            return false;
        }
        session.setLastHeartbeatAt(LocalDateTime.now());
        editorSessionRepository.save(session);
        return true;
    }


    /**
     * Resolves who should be told that a file was opened.
     *
     * <p>{@code created_by_user_id} is the intended answer but it is null on 39 of the 101 active
     * files in the running instance: the column was added later, and files uploaded through an API
     * key without an identity never had one. Falling back through the remaining signals is what
     * turns the feature on for those files instead of silently notifying nobody.
     *
     * <p>Order matters — each step is a weaker claim than the one before it:
     * <ol>
     *   <li>the recorded author's cédula;</li>
     *   <li>the owner of a private file ({@code user_id});</li>
     *   <li>whoever shared it, which is a good proxy for whoever cares about it;</li>
     *   <li>the display name resolved against the project's user directory.</li>
     * </ol>
     *
     * @return the recipient's cédula, or {@code null} when no signal identifies anyone
     */
    private String resolveFileOwner(FileEntity file) {
        String createdBy = trimToNull(file.getCreatedByUserId());
        if (createdBy != null) {
            return createdBy;
        }

        String owner = trimToNull(file.getUserId());
        if (owner != null) {
            return owner;
        }

        try {
            String sharedBy = sharePermissionRepository
                    .findAllByResourceTypeAndResourceId(
                            com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, file.getId())
                    .stream()
                    .map(com.officeplatform.entity.SharePermissionEntity::getSharedByUserId)
                    .map(EditorServiceImpl::trimToNull)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (sharedBy != null) {
                return sharedBy;
            }
        } catch (RuntimeException e) {
            log.debug("No se pudo resolver el autor por permisos del archivo {}: {}",
                    file.getId(), e.getMessage());
        }

        String createdByName = trimToNull(file.getCreatedByName());
        if (createdByName != null && file.getApiKeyId() != null) {
            try {
                return knownUserRepository
                        .findAllByApiKeyIdAndDisplayNameContainingIgnoreCase(file.getApiKeyId(), createdByName)
                        .stream()
                        // Coincidencia exacta: "Ana" no debe resolver a "Ana María".
                        .filter(u -> createdByName.equalsIgnoreCase(trimToNull(u.getDisplayName())))
                        .map(u -> trimToNull(u.getUserId()))
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            } catch (RuntimeException e) {
                log.debug("No se pudo resolver el autor por nombre del archivo {}: {}",
                        file.getId(), e.getMessage());
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
