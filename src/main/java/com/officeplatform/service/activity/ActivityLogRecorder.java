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
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.KnownUserRepository;
import com.officeplatform.service.user.KnownUserService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ActivityLogRecorder {

    private final ActivityLogWriter activityLogWriter;
    private final KnownUserService knownUserService;
    private final KnownUserRepository knownUserRepository;
    private final ApiKeyRepository apiKeyRepository;

    public ActivityLogRecorder(
            ActivityLogWriter activityLogWriter,
            KnownUserService knownUserService,
            KnownUserRepository knownUserRepository,
            ApiKeyRepository apiKeyRepository) {
        this.activityLogWriter = activityLogWriter;
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

            // Written in its own transaction: a failure here must not roll back the caller.
            activityLogWriter.write(entity);
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

    /**
     * Cabeceras que los proxies usan para conservar la IP original, en orden de confianza.
     *
     * <p>{@code X-Forwarded-For} es el estándar de hecho; el resto lo escriben servidores y
     * balanceadores viejos que siguen vivos en muchas redes corporativas. Se recorren en orden
     * porque la primera que aparezca es la del proxy más cercano al cliente.
     */
    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    /**
     * IP real de quien hizo la petición, atravesando los proxies del camino.
     *
     * <p>Con un proxy inverso adelante —lo habitual en una red corporativa— todas las peticiones
     * llegan con la IP del proxy o del gateway de Docker. La bitácora terminaba anotando
     * {@code 172.18.0.1} en cada fila: técnicamente cierto y completamente inútil para una
     * auditoría, que necesita saber desde qué máquina se hizo algo.
     *
     * <p>El localhost en IPv6 se normaliza a {@code 127.0.0.1}: son la misma máquina, y ver dos
     * formas distintas de lo mismo en el listado hace dudar de lo que se está mirando.
     */
    private String resolveClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        for (String cabecera : PROXY_HEADERS) {
            String valor = request.getHeader(cabecera);
            String candidata = firstUsableIp(valor);
            if (candidata != null) {
                return candidata;
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    /**
     * Primera IP utilizable de una cabecera que puede traer una cadena de saltos.
     *
     * <p>{@code X-Forwarded-For} acumula cada proxy que la petición atravesó, separados por comas:
     * la primera es la del cliente. Se descartan los valores vacíos y los "unknown" que algunos
     * proxies escriben cuando no la conocen — tomarlos tal cual dejaría la palabra "unknown" como
     * dirección en la bitácora.
     */
    private static String firstUsableIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        for (String parte : headerValue.split(",")) {
            String ip = parte.trim();
            if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return normalizeIp(ip);
            }
        }
        return null;
    }

    /** El localhost de IPv6 y el de IPv4 son la misma máquina: se anota uno solo. */
    static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String limpia = ip.trim();
        if ("0:0:0:0:0:0:0:1".equals(limpia) || "::1".equals(limpia)) {
            return "127.0.0.1";
        }
        return limpia;
    }

}
