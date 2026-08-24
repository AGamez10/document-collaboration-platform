package com.officeplatform.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Principal que identifica a quien realizó la llamada a la API.
 *
 * <p>En el flujo clásico (X-Api-Key) {@code userId} y {@code userName}
 * permanecen {@code null}; los controladores los leen del query param.
 *
 * <p>En el flujo Widget Token (Bearer) el {@link
 * com.officeplatform.security.widget.WidgetTokenFilter} los rellena con
 * la cédula y el nombre extraídos del JWT, de modo que los controladores
 * existentes sigan funcionando sin ningún cambio.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyPrincipal {

    /** ID interno de la API key (siempre presente). */
    private Long apiKeyId;

    /** Nombre de la API key (siempre presente). */
    private String apiKeyName;

    /**
     * ID del usuario final (cédula en el flujo Widget Token, o el valor
     * del query param {@code userId} en el flujo clásico). Puede ser {@code null}.
     */
    private String userId;

    /**
     * Nombre del usuario final. Puede ser {@code null}.
     */
    private String userName;

    /**
     * Constructor de compatibilidad con el código existente que solo pasa
     * apiKeyId y apiKeyName (flujo clásico X-Api-Key).
     */
    public ApiKeyPrincipal(Long apiKeyId, String apiKeyName) {
        this.apiKeyId   = apiKeyId;
        this.apiKeyName = apiKeyName;
        this.userId     = null;
        this.userName   = null;
    }

    /**
     * Resuelve el userId a utilizar: prioriza el parámetro explícito si está
     * presente, de lo contrario usa la cédula inyectada en este principal.
     */
    public String resolveUserId(String providedUserId) {
        if (providedUserId != null && !providedUserId.isBlank()) {
            return providedUserId.trim();
        }
        return this.userId;
    }

    /**
     * Resuelve el userName a utilizar: prioriza el parámetro explícito si está
     * presente, de lo contrario usa el nombre inyectado en este principal.
     */
    public String resolveUserName(String providedUserName) {
        if (providedUserName != null && !providedUserName.isBlank()) {
            return providedUserName.trim();
        }
        return this.userName;
    }

}


