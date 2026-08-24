package com.officeplatform.service.activity;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import com.officeplatform.entity.ActivityAction;
import com.officeplatform.entity.ActivityLogEntity;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.repository.ActivityLogRepository;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.service.user.KnownUserService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ActivityLogRecorder {

    private final ActivityLogRepository activityLogRepository;
    private final KnownUserService knownUserService;
    private final KnownUserRepository knownUserRepository;
    private final ApiKeyRepository apiKeyRepository;

    public ActivityLogRecorder(
            ActivityLogRepository activityLogRepository,
            KnownUserService knownUserService,
            KnownUserRepository knownUserRepository,
            ApiKeyRepository apiKeyRepository) {
        this.activityLogRepository = activityLogRepository;
        this.knownUserService = knownUserService;
        this.knownUserRepository = knownUserRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    public void record(
            Long apiKeyId,
            String userId,
            String userName,
            ActivityAction action,
            Long fileId,
            String fileName,
            String details) {
        record(apiKeyId, userId, userName, action, fileId, fileName, details, null);
    }

    public void record(
            Long apiKeyId,
            String userId,
            String userName,
            ActivityAction action,
            Long fileId,
            String fileName,
            String details,
            Long folderId) {

        String resolvedUserName = resolveUserName(apiKeyId, userId, userName);

        try {
            ActivityLogEntity entity = ActivityLogEntity.builder()
                    .apiKeyId(apiKeyId)
                    .userId(userId)
                    .userName(resolvedUserName)
                    .action(action)
                    .fileId(fileId)
                    .fileName(fileName)
                    .folderId(folderId)
                    .details(details)
                    .ipAddress(resolveClientIp())
                    .timestamp(LocalDateTime.now())
                    .build();

            activityLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("No se pudo registrar la actividad {} para el archivo {}: {}", action, fileId, e.getMessage());
        }

        // Auto-populate the known-user directory from the same choke-point.
        try {
            knownUserService.registerActivity(apiKeyId, userId, resolvedUserName);
        } catch (Exception e) {
            log.warn("No se pudo registrar al usuario conocido {} (apiKeyId={}): {}", userId, apiKeyId, e.getMessage());
        }
    }

    private String resolveUserName(Long apiKeyId, String userId, String userName) {
        if (userName != null && !userName.isBlank()) {
            return userName.trim();
        }
        if (userId != null && !userId.isBlank() && apiKeyId != null) {
            try {
                KnownUserEntity known = knownUserRepository.findByApiKeyIdAndUserId(apiKeyId, userId.trim()).orElse(null);
                if (known != null && known.getDisplayName() != null && !known.getDisplayName().isBlank()) {
                    return known.getDisplayName();
                }
            } catch (Exception ignored) {}
            return "Usuario (" + userId.trim() + ")";
        }
        if (apiKeyId != null) {
            try {
                ApiKeyEntity key = apiKeyRepository.findById(apiKeyId).orElse(null);
                if (key != null && key.getName() != null) {
                    return key.getName() + " (API)";
                }
            } catch (Exception ignored) {}
            return "Aplicativo #" + apiKeyId;
        }
        return "Sistema";
    }

    private String resolveClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
