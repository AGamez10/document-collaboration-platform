package com.officeplatform.service.admin;

import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.dto.request.BackupConfigRequest;
import com.officeplatform.dto.request.CreateApiKeyRequest;
import com.officeplatform.dto.response.ActiveDocumentSummary;
import com.officeplatform.dto.response.ActivityLogResponse;
import com.officeplatform.dto.response.AdminFileResponse;
import com.officeplatform.dto.response.ApiKeyCreatedResponse;
import com.officeplatform.dto.response.ApiKeyResponse;
import com.officeplatform.dto.response.BackupConfigResponse;
import com.officeplatform.dto.response.BackupInfoResponse;
import com.officeplatform.dto.response.DailyActivityCount;
import com.officeplatform.dto.response.DashboardResponse;
import com.officeplatform.dto.response.EditorSessionResponse;
import com.officeplatform.dto.response.KnownUserResponse;
import com.officeplatform.dto.response.OnlyOfficeStatusResponse;
import com.officeplatform.dto.response.PagedResponse;
import com.officeplatform.dto.response.TopApiKeyUsage;
import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.ActivityLogEntity;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.EditorSessionEntity;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.exception.ApiKeyNotFoundException;
import com.officeplatform.exception.FileNotFoundException;
import com.officeplatform.exception.StorageException;
import com.officeplatform.onlyoffice.service.OnlyOfficeService;
import com.officeplatform.repository.ActivityLogRepository;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.EditorSessionRepository;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.repository.FolderRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.service.activity.ActivityLogRecorder;
import com.officeplatform.service.backup.BackupService;
import com.officeplatform.service.storage.StorageService;
import com.officeplatform.service.user.KnownUserService;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private static final int ACTIVITY_WINDOW_DAYS = 30;
    private static final int TOP_API_KEYS_LIMIT = 5;
    private static final String API_KEY_PREFIX = "opk_";
    private static final int API_KEY_RANDOM_BYTES = 32;
    private static final int MASK_PREFIX_LENGTH = 6;
    private static final int MASK_SUFFIX_LENGTH = 4;
    private static final String MASK_FILLER = "••••••••";

    private final FileRepository fileRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ActivityLogRepository activityLogRepository;
    private final FolderRepository folderRepository;
    private final StorageService storageService;
    private final EditorSessionRepository editorSessionRepository;
    private final OnlyOfficeService onlyOfficeService;
    private final KnownUserService knownUserService;
    private final KnownUserRepository knownUserRepository;
    private final ActivityLogRecorder activityLogRecorder;
    private final BackupService backupService;
    private final int onlyOfficeMaxConnections;
    private final long storageLimit;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminServiceImpl(
            FileRepository fileRepository,
            ApiKeyRepository apiKeyRepository,
            ActivityLogRepository activityLogRepository,
            FolderRepository folderRepository,
            StorageService storageService,
            EditorSessionRepository editorSessionRepository,
            OnlyOfficeService onlyOfficeService,
            KnownUserService knownUserService,
            KnownUserRepository knownUserRepository,
            ActivityLogRecorder activityLogRecorder,
            BackupService backupService,
            @Value("${office-platform.onlyoffice.max-connections}") int onlyOfficeMaxConnections,
            @Value("${office-platform.storage.max-total-size}") long storageLimit) {
        this.fileRepository = fileRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.activityLogRepository = activityLogRepository;
        this.folderRepository = folderRepository;
        this.storageService = storageService;
        this.editorSessionRepository = editorSessionRepository;
        this.onlyOfficeService = onlyOfficeService;
        this.knownUserService = knownUserService;
        this.knownUserRepository = knownUserRepository;
        this.activityLogRecorder = activityLogRecorder;
        this.backupService = backupService;
        this.onlyOfficeMaxConnections = onlyOfficeMaxConnections;
        this.storageLimit = storageLimit;
    }

    @Override
    public DashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();

        long totalFiles = fileRepository.countByDeletedAtIsNull();
        long activeApiKeys = apiKeyRepository.countByActiveTrue();
        long storageUsedBytes = fileRepository.sumSizeOfActiveFiles();
        long filesCreatedToday = fileRepository.countByCreatedAtAfter(today.atStartOfDay());
        long filesCreatedThisWeek = fileRepository.countByCreatedAtAfter(today.minusDays(6).atStartOfDay());
        long filesCreatedThisMonth = fileRepository.countByCreatedAtAfter(today.minusDays(29).atStartOfDay());

        DashboardResponse response = new DashboardResponse();
        response.setTotalFiles(totalFiles);
        response.setActiveApiKeys(activeApiKeys);
        response.setStorageUsedBytes(storageUsedBytes);
        response.setFilesCreatedToday(filesCreatedToday);
        response.setFilesCreatedThisWeek(filesCreatedThisWeek);
        response.setFilesCreatedThisMonth(filesCreatedThisMonth);
        response.setActivityLast30Days(buildActivityLast30Days(today));
        response.setStorageLimit(storageLimit);
        response.setTopApiKeys(buildTopApiKeys());
        return response;
    }

    private List<TopApiKeyUsage> buildTopApiKeys() {
        Map<Long, String> namesById = new HashMap<>();
        apiKeyRepository.findAll().forEach(key -> namesById.put(key.getId(), key.getName()));

        return fileRepository.countAndSizeByApiKeyGrouped().stream()
                .map(row -> {
                    Long apiKeyId = (Long) row[0];
                    long fileCount = (Long) row[1];
                    long storageUsed = (Long) row[2];
                    String name = namesById.getOrDefault(apiKeyId, "API key #" + apiKeyId);
                    return new TopApiKeyUsage(name, fileCount, storageUsed);
                })
                .sorted(Comparator.comparingLong(TopApiKeyUsage::getStorageUsed).reversed())
                .limit(TOP_API_KEYS_LIMIT)
                .toList();
    }

    private List<DailyActivityCount> buildActivityLast30Days(LocalDate today) {
        LocalDate windowStart = today.minusDays(ACTIVITY_WINDOW_DAYS - 1L);
        LocalDateTime cutoff = windowStart.atStartOfDay();

        Map<LocalDate, Long> countsByDay = new HashMap<>();
        for (ActivityLogEntity entry : activityLogRepository.findAllByTimestampAfter(cutoff)) {
            LocalDate day = entry.getTimestamp().toLocalDate();
            countsByDay.merge(day, 1L, Long::sum);
        }

        List<DailyActivityCount> result = new ArrayList<>(ACTIVITY_WINDOW_DAYS);
        for (int i = 0; i < ACTIVITY_WINDOW_DAYS; i++) {
            LocalDate day = windowStart.plusDays(i);
            result.add(new DailyActivityCount(day.toString(), countsByDay.getOrDefault(day, 0L)));
        }
        return result;
    }

    @Override
    public List<ApiKeyResponse> listApiKeys() {
        return apiKeyRepository.findAll().stream()
                .map(this::toApiKeyResponse)
                .toList();
    }

    @Override
    @Transactional
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request) {
        String rawKey = generateApiKey();

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .apiKey(rawKey)
                .name(request.getName())
                .active(true)
                .build();

        ApiKeyEntity saved = apiKeyRepository.save(entity);

        return new ApiKeyCreatedResponse(saved.getId(), saved.getName(), rawKey, saved.getActive(), saved.getCreatedAt());
    }

    @Override
    @Transactional
    public ApiKeyResponse updateApiKeyStatus(Long id, boolean active) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ApiKeyNotFoundException(id));

        entity.setActive(active);
        ApiKeyEntity saved = apiKeyRepository.save(entity);
        return toApiKeyResponse(saved);
    }

    @Override
    @Transactional
    public void deleteApiKey(Long id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ApiKeyNotFoundException(id));
        apiKeyRepository.delete(entity);
    }

    @Override
    public PagedResponse<ActivityLogResponse> getActivityLog(
            Pageable pageable, LocalDate dateFrom, LocalDate dateTo, Long apiKeyId, String action, String userId,
            Long folderId) {

        Specification<ActivityLogEntity> spec =
                buildActivityLogSpecification(dateFrom, dateTo, apiKeyId, action, userId, folderId);
        Page<ActivityLogEntity> page = activityLogRepository.findAll(spec, pageable);

        Map<Long, String> apiKeyNamesById = new HashMap<>();
        apiKeyRepository.findAll().forEach(key -> apiKeyNamesById.put(key.getId(), key.getName()));

        Map<Long, String> folderNamesById = new HashMap<>();
        folderRepository.findAll().forEach(folder -> folderNamesById.put(folder.getId(), folder.getName()));

        List<ActivityLogResponse> content = page.getContent().stream()
                .map(entity -> toActivityLogResponse(entity, apiKeyNamesById.get(entity.getApiKeyId()), folderNamesById.get(entity.getFolderId())))
                .toList();

        return new PagedResponse<>(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private Specification<ActivityLogEntity> buildActivityLogSpecification(
            LocalDate dateFrom, LocalDate dateTo, Long apiKeyId, String action, String userId, Long folderId) {

        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (dateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("timestamp"), dateTo.atTime(23, 59, 59)));
            }
            if (apiKeyId != null) {
                predicates.add(criteriaBuilder.equal(root.get("apiKeyId"), apiKeyId));
            }
            if (userId != null && !userId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            if (folderId != null) {
                predicates.add(criteriaBuilder.equal(root.get("folderId"), folderId));
            }
            if (action != null && !action.isBlank()) {
                try {
                    ActivityAction parsedAction = ActivityAction.valueOf(action.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("action"), parsedAction));
                } catch (IllegalArgumentException e) {
                    // Unknown action value: ignore filter
                }
            }

            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    public List<AdminFileResponse> listFiles(Long apiKeyId, boolean trashed) {
        List<FileEntity> files;
        if (apiKeyId != null) {
            files = trashed
                    ? fileRepository.findAllByApiKeyIdAndDeletedAtIsNotNull(apiKeyId)
                    : fileRepository.findAllByApiKeyIdAndDeletedAtIsNull(apiKeyId);
        } else {
            files = trashed ? fileRepository.findAllByDeletedAtIsNotNull() : fileRepository.findAllByDeletedAtIsNull();
        }

        Map<Long, String> apiKeyNamesById = new HashMap<>();
        apiKeyRepository.findAll().forEach(key -> apiKeyNamesById.put(key.getId(), key.getName()));

        Map<Long, FolderEntity> foldersById = new HashMap<>();
        folderRepository.findAll().forEach(folder -> foldersById.put(folder.getId(), folder));

        return files.stream()
                .map(file -> toAdminFileResponse(file, apiKeyNamesById.get(file.getApiKeyId()), foldersById))
                .toList();
    }

    private String buildFolderPath(Long folderId, Map<Long, FolderEntity> foldersById) {
        if (folderId == null) {
            return "Raíz";
        }
        List<String> segments = new ArrayList<>();
        Long currentId = folderId;
        while (currentId != null) {
            FolderEntity folder = foldersById.get(currentId);
            if (folder == null) {
                break;
            }
            segments.add(0, folder.getName());
            currentId = folder.getParentId();
        }
        return "Raíz / " + String.join(" / ", segments);
    }

    @Override
    @Transactional
    public void restoreFile(Long fileId) {
        FileEntity fileEntity = fileRepository.findByIdAndDeletedAtIsNotNull(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        fileEntity.setDeletedAt(null);
        fileEntity.setUpdatedByName("Admin");
        FileEntity saved = fileRepository.save(fileEntity);

        activityLogRecorder.record(saved.getApiKeyId(), "admin", "Administrador", ActivityAction.RESTORE,
                saved.getId(), saved.getOriginalFileName(), "Restaurado desde el panel de administración", saved.getFolderId());
    }

    @Override
    @Transactional
    public void purgeFile(Long fileId) {
        FileEntity fileEntity = fileRepository.findByIdAndDeletedAtIsNotNull(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        storageService.delete(fileEntity.getObjectName());
        fileRepository.delete(fileEntity);

        activityLogRecorder.record(fileEntity.getApiKeyId(), "admin", "Administrador", ActivityAction.PURGE,
                fileEntity.getId(), fileEntity.getOriginalFileName(), "Purgado definitivamente desde el panel admin", fileEntity.getFolderId());
    }

    @Override
    public OnlyOfficeStatusResponse getOnlyOfficeStatus() {
        List<EditorSessionEntity> activeSessions = editorSessionRepository.findAllByClosedAtIsNull();

        Map<Long, List<EditorSessionEntity>> byFile = activeSessions.stream()
                .collect(Collectors.groupingBy(EditorSessionEntity::getFileId));

        List<ActiveDocumentSummary> documents = byFile.values().stream()
                .map(sessions -> {
                    EditorSessionEntity first = sessions.get(0);
                    List<String> users = sessions.stream()
                            .map(s -> s.getUserName() != null ? s.getUserName() : s.getUserId())
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    LocalDateTime earliestOpen = sessions.stream()
                            .map(EditorSessionEntity::getOpenedAt)
                            .min(LocalDateTime::compareTo)
                            .orElse(first.getOpenedAt());
                    return new ActiveDocumentSummary(
                            "file_" + first.getFileId(), first.getFileName(), users, earliestOpen);
                })
                .toList();

        long active = activeSessions.size();
        int usage = onlyOfficeMaxConnections > 0 ? (int) Math.round((active * 100.0) / onlyOfficeMaxConnections) : 0;

        return new OnlyOfficeStatusResponse(active, onlyOfficeMaxConnections, usage, documents);
    }

    @Override
    public List<EditorSessionResponse> getEditorSessions(Boolean active, Long apiKeyId) {
        boolean activeOnly = active == null || active;
        List<EditorSessionEntity> sessions;

        if (activeOnly && apiKeyId != null) {
            sessions = editorSessionRepository.findAllByClosedAtIsNullAndApiKeyId(apiKeyId);
        } else if (activeOnly) {
            sessions = editorSessionRepository.findAllByClosedAtIsNull();
        } else if (apiKeyId != null) {
            sessions = editorSessionRepository.findAll().stream()
                    .filter(s -> s.getApiKeyId().equals(apiKeyId))
                    .toList();
        } else {
            sessions = editorSessionRepository.findAll();
        }

        Map<Long, String> apiKeyNamesById = new HashMap<>();
        apiKeyRepository.findAll().forEach(key -> apiKeyNamesById.put(key.getId(), key.getName()));

        return sessions.stream()
                .sorted(Comparator.comparing(EditorSessionEntity::getOpenedAt).reversed())
                .map(s -> toEditorSessionResponse(s, apiKeyNamesById.get(s.getApiKeyId())))
                .toList();
    }

    @Override
    @Transactional
    public void forceCloseSession(Long sessionId) {
        EditorSessionEntity session = editorSessionRepository.findByIdAndClosedAtIsNull(sessionId)
                .orElseThrow(() -> new StorageException("La sesión no existe o ya está cerrada"));

        try {
            onlyOfficeService.dropUser(session.getDocumentKey(), session.getUserId());
        } catch (Exception e) {
            log.warn("No se pudo desconectar al usuario de OnlyOffice (sessionId={}, documentKey={}): {}",
                    sessionId, session.getDocumentKey(), e.getMessage());
        }

        session.setClosedAt(LocalDateTime.now());
        editorSessionRepository.save(session);
    }

    @Override
    public List<KnownUserResponse> listUsers(Long apiKeyId, String search) {
        Map<Long, String> apiKeyNamesById = new HashMap<>();
        apiKeyRepository.findAll().forEach(key -> apiKeyNamesById.put(key.getId(), key.getName()));

        List<KnownUserEntity> users;
        if (apiKeyId != null) {
            users = knownUserRepository.findAllByApiKeyId(apiKeyId);
        } else {
            users = knownUserRepository.findAll();
        }

        if (search != null && !search.isBlank()) {
            String term = search.trim().toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(term)) ||
                                 (u.getUserId() != null && u.getUserId().toLowerCase().contains(term)))
                    .toList();
        }

        return users.stream()
                .sorted(Comparator.comparing(KnownUserEntity::getLastSeenAt).reversed())
                .map(u -> KnownUserResponse.builder()
                        .id(u.getId())
                        .apiKeyId(u.getApiKeyId())
                        .apiKeyName(apiKeyNamesById.getOrDefault(u.getApiKeyId(), "Proyecto #" + u.getApiKeyId()))
                        .userId(u.getUserId())
                        .displayName(u.getDisplayName())
                        .role(u.getRole())
                        .firstSeenAt(u.getFirstSeenAt())
                        .lastSeenAt(u.getLastSeenAt())
                        .build())
                .toList();
    }

    @Override
    public KnownUserResponse updateUserRole(Long knownUserId, String role) {
        KnownUserEntity user = knownUserService.updateRole(knownUserId, role);
        String apiKeyName = apiKeyRepository.findById(user.getApiKeyId())
                .map(ApiKeyEntity::getName)
                .orElse("Proyecto #" + user.getApiKeyId());

        return KnownUserResponse.builder()
                .id(user.getId())
                .apiKeyId(user.getApiKeyId())
                .apiKeyName(apiKeyName)
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .firstSeenAt(user.getFirstSeenAt())
                .lastSeenAt(user.getLastSeenAt())
                .build();
    }

    @Override
    public List<BackupInfoResponse> listBackups() {
        return backupService.listBackups();
    }

    @Override
    public BackupInfoResponse createBackup(String type) {
        return backupService.createBackup(type);
    }

    @Override
    public InputStream getBackupStream(String fileName) {
        return backupService.getBackupStream(fileName);
    }

    @Override
    public void deleteBackup(String fileName) {
        backupService.deleteBackup(fileName);
    }

    @Override
    public BackupConfigResponse getBackupConfig() {
        return backupService.getConfig();
    }

    @Override
    public BackupConfigResponse updateBackupConfig(BackupConfigRequest request) {
        return backupService.updateConfig(request);
    }

    private EditorSessionResponse toEditorSessionResponse(EditorSessionEntity entity, String apiKeyName) {
        return new EditorSessionResponse(
                entity.getId(),
                entity.getFileId(),
                entity.getFileName(),
                entity.getUserId(),
                entity.getUserName(),
                entity.getApiKeyId(),
                apiKeyName,
                entity.getOpenedAt(),
                entity.getClosedAt());
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String maskApiKey(String rawKey) {
        if (rawKey.length() <= MASK_PREFIX_LENGTH + MASK_SUFFIX_LENGTH) {
            return MASK_FILLER;
        }
        String prefix = rawKey.substring(0, MASK_PREFIX_LENGTH);
        String suffix = rawKey.substring(rawKey.length() - MASK_SUFFIX_LENGTH);
        return prefix + MASK_FILLER + suffix;
    }

    private ApiKeyResponse toApiKeyResponse(ApiKeyEntity entity) {
        return new ApiKeyResponse(
                entity.getId(), entity.getName(), maskApiKey(entity.getApiKey()), entity.getActive(), entity.getCreatedAt());
    }

    private ActivityLogResponse toActivityLogResponse(ActivityLogEntity entity, String apiKeyName, String folderName) {
        return ActivityLogResponse.builder()
                .id(entity.getId())
                .apiKeyId(entity.getApiKeyId())
                .apiKeyName(apiKeyName)
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .action(entity.getAction().name())
                .fileId(entity.getFileId())
                .fileName(entity.getFileName())
                .folderId(entity.getFolderId())
                .folderName(folderName)
                .details(entity.getDetails())
                .ipAddress(entity.getIpAddress())
                .timestamp(entity.getTimestamp())
                .build();
    }

    private AdminFileResponse toAdminFileResponse(FileEntity entity, String apiKeyName, Map<Long, FolderEntity> foldersById) {
        return new AdminFileResponse(
                entity.getId(),
                entity.getUuid(),
                entity.getOriginalFileName(),
                entity.getMimeType(),
                entity.getSize(),
                entity.getApiKeyId(),
                apiKeyName,
                entity.getFolderId(),
                buildFolderPath(entity.getFolderId(), foldersById),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }

}
