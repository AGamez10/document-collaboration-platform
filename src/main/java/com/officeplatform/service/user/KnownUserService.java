package com.officeplatform.service.user;

import java.util.List;

import com.officeplatform.entity.KnownUserEntity;

public interface KnownUserService {

    /**
     * Upserts the acting user into the known-user directory for the given project. Creates the
     * record on first sight (role "user"), otherwise refreshes {@code lastSeenAt} (and the display
     * name if a newer one is provided). No-op when {@code userId} is null/blank (classic X-Api-Key
     * flow with no identified end user).
     */
    void registerActivity(Long apiKeyId, String userId, String displayName);

    List<KnownUserEntity> listUsers(Long apiKeyId);

    List<KnownUserEntity> searchUsers(Long apiKeyId, String term);

    /** Changes a known user's role ("user" | "admin"). Used by the platform-admin panel. */
    KnownUserEntity updateRole(Long knownUserId, String role);

    /**
     * Borra una inscripción del registro de usuarios conocidos.
     *
     * <p>No borra archivos ni permisos: {@code known_users} es la libreta de quién pasó por cada
     * proyecto, no la identidad de la persona. Eliminar la fila quita a alguien de los listados y
     * de los selectores de compartir; lo que esa persona haya creado sigue donde está, con su
     * autoría intacta, que es lo que una auditoría necesita.
     */
    void deleteUser(Long knownUserId);

}
