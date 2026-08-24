package com.officeplatform.security.widget;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.officeplatform.exception.InvalidWidgetTokenException;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.security.model.ApiKeyPrincipal;

import io.jsonwebtoken.Claims;

/**
 * Filtro de seguridad que procesa el header {@code Authorization: Bearer <widgetToken>}.
 *
 * <p>Si el header está presente y el JWT es válido:
 * <ol>
 *   <li>Extrae {@code cedula}, {@code nombre} y {@code apiKeyId} del JWT.</li>
 *   <li>Verifica que la API key siga activa en la base de datos.</li>
 *   <li>Construye un {@link ApiKeyPrincipal} con la cédula como {@code userId}
 *       y el nombre como {@code userName}.</li>
 *   <li>Autentica el request en el {@code SecurityContext}.</li>
 * </ol>
 *
 * <p>Si el header no está presente, el filtro deja pasar el request sin
 * modificar el contexto de seguridad (el {@code ApiKeyFilter} normal puede
 * autenticarlo vía {@code X-Api-Key}).
 *
 * <p>Si el token está presente pero inválido se responde 401 directamente.
 */
@Component
public class WidgetTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final WidgetTokenService widgetTokenService;
    private final ApiKeyRepository   apiKeyRepository;

    public WidgetTokenFilter(WidgetTokenService widgetTokenService,
                             ApiKeyRepository apiKeyRepository) {
        this.widgetTokenService = widgetTokenService;
        this.apiKeyRepository   = apiKeyRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.startsWith("/api/onlyoffice/callback") || path.startsWith("/api/files/download/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Si no hay Bearer token, dejamos pasar (el ApiKeyFilter lo manejará).
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = widgetTokenService.validateToken(token);

            String cedula    = claims.get(WidgetTokenService.CLAIM_CEDULA,    String.class);
            String nombre    = claims.get(WidgetTokenService.CLAIM_NOMBRE,    String.class);
            Long   apiKeyId  = claims.get(WidgetTokenService.CLAIM_API_KEY_ID, Integer.class).longValue();

            // Verificar que la API key siga activa (podría haber sido revocada después de emitir el token)
            boolean active = apiKeyRepository.findById(apiKeyId)
                    .map(k -> Boolean.TRUE.equals(k.getActive()))
                    .orElse(false);

            if (!active) {
                sendUnauthorized(response, "La API key asociada al token ha sido revocada o no existe");
                return;
            }

            // userId = cédula (identificador canónico global)
            // userName = nombre para mostrar en el editor
            ApiKeyPrincipal principal = new ApiKeyPrincipal(apiKeyId, nombre, cedula, nombre);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (InvalidWidgetTokenException ex) {
            sendUnauthorized(response, ex.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message.replace("\"", "'") + "\"}"
        );
    }

}
