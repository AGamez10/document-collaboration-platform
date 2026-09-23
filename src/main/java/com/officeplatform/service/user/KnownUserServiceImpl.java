package com.officeplatform.service.user;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                .role(roleAlreadyDecidedFor(resolvedUserId))
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

    /**
     * Rol con el que nace una inscripción: el que esa persona ya tiene en otro proyecto.
     *
     * <p>El rol se administra por persona, no por proyecto — al promover a alguien,
     * {@code AdminServiceImpl.updateUserRole} escribe el nuevo rol en <b>todas</b> sus filas. Pero
     * esa sincronización solo alcanza a las filas que existen, y una inscripción se crea recién
     * cuando la persona entra por primera vez a ese proyecto. Un administrador promovido hoy
     * entraba mañana a un proyecto nuevo y aparecía como usuario común, sin que nadie hubiera
     * degradado nada: la promoción simplemente no tenía dónde escribirse.
     *
     * <p>Basta con encontrarlo como administrador en algún lado. Degradarlo también sincroniza
     * todas sus filas, así que un "admin" remanente significa que la decisión vigente es esa.
     */
    private String roleAlreadyDecidedFor(String userId) {
        boolean adminEnOtroProyecto = knownUserRepository.findAllByUserId(userId).stream()
                .anyMatch(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()));
        return adminEnOtroProyecto ? ROLE_ADMIN : ROLE_USER;
    }

    /** Refreshes last-seen and, when a name is supplied, the display name. */
    private void applyVisit(KnownUserEntity user, String resolvedName, LocalDateTime now) {
        user.setLastSeenAt(now);
        if (resolvedName != null) {
            user.setDisplayName(resolvedName);
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long knownUserId) {
        if (knownUserId == null || !knownUserRepository.existsById(knownUserId)) {
            // Un borrado sobre algo que no existe se avisa en vez de responder éxito: en un panel
            // de administración, un "listo" sobre una fila que sigue ahí es peor que un error.
            throw new StorageException("El usuario no existe: " + knownUserId);
        }
        knownUserRepository.deleteById(knownUserId);
    }

    @Override
    public List<KnownUserEntity> listUsers(Long apiKeyId) {
        List<KnownUserEntity> all = knownUserRepository.findAllByOrderByLastSeenAtDesc();
        return deduplicateUsers(all, apiKeyId);
    }

    @Override
    public List<KnownUserEntity> searchUsers(Long apiKeyId, String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        List<KnownUserEntity> matched = knownUserRepository.searchAllByTerm(term.trim());
        return deduplicateUsers(matched, apiKeyId);
    }

    private List<KnownUserEntity> deduplicateUsers(List<KnownUserEntity> users, Long preferredApiKeyId) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        Map<String, KnownUserEntity> unique = new LinkedHashMap<>();
        for (KnownUserEntity u : users) {
            String uid = u.getUserId();
            if (uid == null || uid.isBlank()) continue;
            KnownUserEntity existing = unique.get(uid);
            if (existing == null) {
                unique.put(uid, u);
            } else if (preferredApiKeyId != null && Objects.equals(u.getApiKeyId(), preferredApiKeyId)
                    && !Objects.equals(existing.getApiKeyId(), preferredApiKeyId)) {
                unique.put(uid, u);
            }
        }
        List<KnownUserEntity> result = new ArrayList<>(unique.values());
        if (preferredApiKeyId != null) {
            result.sort((a, b) -> {
                boolean aPref = Objects.equals(a.getApiKeyId(), preferredApiKeyId);
                boolean bPref = Objects.equals(b.getApiKeyId(), preferredApiKeyId);
                if (aPref && !bPref) return -1;
                if (!aPref && bPref) return 1;
                return 0;
            });
        }
        return result;
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
