package com.officeplatform.security.widget;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.officeplatform.exception.InvalidWidgetTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Genera y valida los Widget Tokens (JWT de corta duración) que permiten
 * identificar un usuario de un aplicativo consumidor mediante su cédula,
 * sin necesidad de exponer la cédula en el HTML ni en el widget.
 *
 * <p>Estos tokens son <b>distintos</b> al JWT de OnlyOffice (gestionado por
 * {@code JwtService}). Usan su propio secreto configurable vía
 * {@code office-platform.widget-token.secret} en application.yml.
 *
 * <p>Claims incluidos en el JWT:
 * <ul>
 *   <li>{@code cedula}    – identificador canónico del usuario</li>
 *   <li>{@code nombre}    – nombre visible en el editor (puede ser nulo)</li>
 *   <li>{@code apiKeyId}  – ID interno de la API key que hizo la llamada</li>
 * </ul>
 */
@Component
public class WidgetTokenService {

    public static final String CLAIM_CEDULA    = "cedula";
    public static final String CLAIM_NOMBRE    = "nombre";
    public static final String CLAIM_API_KEY_ID = "apiKeyId";

    /** 8 horas en segundos. */
    public static final int EXPIRES_IN_SECONDS = 28_800;

    private final SecretKey secretKey;

    public WidgetTokenService(
            @Value("${office-platform.widget-token.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * Genera un JWT firmado con HS256 que identifica al usuario por cédula.
     *
     * @param cedula    cédula del usuario (ya validada)
     * @param nombre    nombre para mostrar (puede ser null o vacío)
     * @param apiKeyId  ID interno de la API key del aplicativo consumidor
     * @return token JWT compacto
     */
    public String generateToken(String cedula, String nombre, Long apiKeyId) {
        long nowMillis = System.currentTimeMillis();
        Date now       = new Date(nowMillis);
        Date expiry    = new Date(nowMillis + (long) EXPIRES_IN_SECONDS * 1_000);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_CEDULA,     cedula);
        claims.put(CLAIM_NOMBRE,     nombre != null ? nombre : "");
        claims.put(CLAIM_API_KEY_ID, apiKeyId);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida el token y retorna sus claims.
     *
     * @throws InvalidWidgetTokenException si el token es inválido o expiró
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidWidgetTokenException("Widget token inválido o expirado: " + ex.getMessage());
        }
    }

}
