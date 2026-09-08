package com.officeplatform.service.portal;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.dto.request.PortalChangePasswordRequest;
import com.officeplatform.dto.request.PortalLoginRequest;
import com.officeplatform.dto.response.PortalLoginResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.entity.PortalUserEntity;
import com.officeplatform.exception.ShareAccessDeniedException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.repository.PortalUserRepository;
import com.officeplatform.security.widget.WidgetTokenService;
import com.officeplatform.service.user.KnownUserService;

import lombok.extern.slf4j.Slf4j;

/**
 * Sign-in for people using the web portal directly, with no consuming application in front.
 *
 * <p>Accounts are created on first sign-in with a provisional password, so nobody has to be
 * enrolled by hand. That convenience is only acceptable because the account is useless until the
 * password is changed: while {@code mustChangePassword} stands, no session token is issued.
 */
@Service
@Slf4j
public class PortalAuthService {

    /** Provisional password every account starts with. Never valid for an actual session. */
    static final String DEFAULT_PASSWORD = "2026";

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final PortalUserRepository portalUserRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final WidgetTokenService widgetTokenService;
    private final KnownUserService knownUserService;
    private final PasswordEncoder passwordEncoder;
    private final String portalApiKeyName;

    public PortalAuthService(
            PortalUserRepository portalUserRepository,
            ApiKeyRepository apiKeyRepository,
            WidgetTokenService widgetTokenService,
            KnownUserService knownUserService,
            PasswordEncoder passwordEncoder,
            @Value("${office-platform.portal.api-key-name:PORTAL WEB}") String portalApiKeyName) {
        this.portalUserRepository = portalUserRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.widgetTokenService = widgetTokenService;
        this.knownUserService = knownUserService;
        this.passwordEncoder = passwordEncoder;
        this.portalApiKeyName = portalApiKeyName;
    }

    /**
     * Authenticates a person by cédula.
     *
     * <p>An unknown cédula is registered on the spot with the provisional password. The supplied
     * password is then verified against the stored hash exactly as for an existing account, so a
     * first-time visitor who types something other than the provisional password is rejected
     * rather than silently let in.
     */
    @Transactional
    public PortalLoginResponse login(PortalLoginRequest request) {
        String cedula = normalize(request.getCedula());
        if (cedula == null) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }

        PortalUserEntity user = portalUserRepository.findByCedula(cedula)
                .orElseGet(() -> register(cedula, request.getNombre()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Same message whether the account exists or not: telling them apart would turn this
            // endpoint into a way of discovering which cédulas are registered.
            throw new ShareAccessDeniedException("Cédula o contraseña incorrecta.");
        }

        // Belt and braces: a stored hash that still matches the provisional password forces the
        // change even if the flag was cleared by hand in the database.
        boolean provisional = user.isMustChangePassword()
                || passwordEncoder.matches(DEFAULT_PASSWORD, user.getPasswordHash());

        if (provisional) {
            return PortalLoginResponse.builder()
                    .success(true)
                    .mustChangePassword(true)
                    .cedula(user.getCedula())
                    .displayName(user.getDisplayName())
                    .build();
        }

        return issueSession(user, request.getNombre());
    }

    /**
     * Replaces the provisional password and returns a usable session.
     *
     * <p>The current password is verified first: without that, anyone who knows a cédula could
     * take over an account that had not been used yet.
     */
    @Transactional
    public PortalLoginResponse changePassword(PortalChangePasswordRequest request) {
        String cedula = normalize(request.getCedula());
        if (cedula == null) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }

        PortalUserEntity user = portalUserRepository.findByCedula(cedula)
                .orElseThrow(() -> new ShareAccessDeniedException("Cédula o contraseña incorrecta."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ShareAccessDeniedException("Cédula o contraseña incorrecta.");
        }

        validateNewPassword(request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        portalUserRepository.save(user);

        log.info("Contraseña de portal actualizada para la cédula {}", cedula);
        return issueSession(user, user.getDisplayName());
    }

    /** Rules for an acceptable password, checked server-side so the browser cannot skip them. */
    private void validateNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La nueva contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        if (DEFAULT_PASSWORD.equals(password)) {
            throw new IllegalArgumentException(
                    "No podés volver a usar la contraseña provisional. Elegí una propia.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException(
                    "La nueva contraseña debe combinar letras y números.");
        }
    }

    /** Creates the account with the provisional password, tolerating a concurrent first sign-in. */
    private PortalUserEntity register(String cedula, String displayName) {
        PortalUserEntity created = PortalUserEntity.builder()
                .cedula(cedula)
                .displayName((displayName != null && !displayName.isBlank()) ? displayName.trim() : null)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .mustChangePassword(true)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            return portalUserRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException race) {
            // Two first sign-ins at once: the unique constraint let exactly one through.
            return portalUserRepository.findByCedula(cedula).orElseThrow(() -> race);
        }
    }

    /** Mints the widget token the portal front-end uses to drive the file manager. */
    private PortalLoginResponse issueSession(PortalUserEntity user, String providedName) {
        ApiKeyEntity portalKey = resolvePortalApiKey();

        String name = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                ? user.getDisplayName()
                : ((providedName != null && !providedName.isBlank()) ? providedName.trim() : user.getCedula());

        if (user.getDisplayName() == null && providedName != null && !providedName.isBlank()) {
            user.setDisplayName(providedName.trim());
        }
        user.setLastLoginAt(LocalDateTime.now());
        portalUserRepository.save(user);

        String token = widgetTokenService.generateToken(user.getCedula(), name, portalKey.getId());

        // Keeps the portal's people visible in the project directory, same as any other consumer.
        try {
            knownUserService.registerActivity(portalKey.getId(), user.getCedula(), name);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar al usuario {} en el directorio del portal: {}",
                    user.getCedula(), e.getMessage());
        }

        return PortalLoginResponse.builder()
                .success(true)
                .mustChangePassword(false)
                .token(token)
                .expiresIn((long) WidgetTokenService.EXPIRES_IN_SECONDS)
                .cedula(user.getCedula())
                .displayName(name)
                .build();
    }

    /**
     * The portal's own API key, created on demand.
     *
     * <p>It exists so portal files land in their own project rather than borrowing a consuming
     * application's key, which would mix two populations in the same shared space.
     */
    private ApiKeyEntity resolvePortalApiKey() {
        return apiKeyRepository.findAll().stream()
                .filter(k -> portalApiKeyName.equalsIgnoreCase(k.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ApiKeyEntity key = new ApiKeyEntity();
                    key.setApiKey("opk_portal_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                    key.setName(portalApiKeyName);
                    key.setActive(Boolean.TRUE);
                    key.setCreatedAt(LocalDateTime.now());
                    log.info("Creada la API key del portal web: {}", portalApiKeyName);
                    return apiKeyRepository.save(key);
                });
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
