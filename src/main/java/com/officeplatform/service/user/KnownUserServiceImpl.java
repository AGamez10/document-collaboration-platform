package com.officeplatform.service.user;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.exception.StorageException;
import com.officeplatform.repository.KnownUserRepository;

@Service
public class KnownUserServiceImpl implements KnownUserService {

    static final String ROLE_USER = "user";
    static final String ROLE_ADMIN = "admin";

    private final KnownUserRepository knownUserRepository;

    public KnownUserServiceImpl(KnownUserRepository knownUserRepository) {
        this.knownUserRepository = knownUserRepository;
    }

    /**
     * Runs in its own transaction (REQUIRES_NEW) so a failure here — e.g. a rare unique-constraint
     * race on first sight of a user — never poisons the caller's business transaction (upload,
     * folder creation, etc.). The caller already swallows the exception.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerActivity(Long apiKeyId, String userId, String displayName) {
        if (apiKeyId == null || userId == null || userId.isBlank()) {
            return;
        }
        String resolvedUserId = userId.trim();
        String resolvedName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : null;
        LocalDateTime now = LocalDateTime.now();

        KnownUserEntity user = knownUserRepository.findByApiKeyIdAndUserId(apiKeyId, resolvedUserId).orElse(null);
        if (user != null) {
            applyVisit(user, resolvedName, now);
            knownUserRepository.save(user);
            return;
        }

        KnownUserEntity created = KnownUserEntity.builder()
                .apiKeyId(apiKeyId)
                .userId(resolvedUserId)
                .displayName(resolvedName)
                .role(ROLE_USER)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();

        try {
            knownUserRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException race) {
            // Two concurrent first-sight requests for the same identifier: the unique constraint on
            // (api_key_id, user_id) let exactly one insert through. Re-read and update that row
            // instead of surfacing a duplicate — the constraint is the source of truth, not the
            // preceding read, which is why the insert is attempted rather than trusted blindly.
            KnownUserEntity existing = knownUserRepository
                    .findByApiKeyIdAndUserId(apiKeyId, resolvedUserId)
                    .orElseThrow(() -> race);
            applyVisit(existing, resolvedName, now);
            knownUserRepository.save(existing);
        }
    }

    /** Refreshes last-seen and, when a name is supplied, the display name. */
    private void applyVisit(KnownUserEntity user, String resolvedName, LocalDateTime now) {
        user.setLastSeenAt(now);
        if (resolvedName != null) {
            user.setDisplayName(resolvedName);
        }
    }

    @Override
    public List<KnownUserEntity> listUsers(Long apiKeyId) {
        return knownUserRepository.findAllByApiKeyId(apiKeyId);
    }

    @Override
    public List<KnownUserEntity> searchUsers(Long apiKeyId, String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return knownUserRepository.findAllByApiKeyIdAndDisplayNameContainingIgnoreCase(apiKeyId, term.trim());
    }

    @Override
    @Transactional
    public KnownUserEntity updateRole(Long knownUserId, String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!ROLE_USER.equals(normalized) && !ROLE_ADMIN.equals(normalized)) {
            throw new StorageException("Rol inválido: debe ser 'user' o 'admin'");
        }
        KnownUserEntity user = knownUserRepository.findById(knownUserId)
                .orElseThrow(() -> new StorageException("El usuario no existe: " + knownUserId));
        user.setRole(normalized);
        return knownUserRepository.save(user);
    }

}
