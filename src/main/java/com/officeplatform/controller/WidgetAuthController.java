package com.officeplatform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.officeplatform.dto.request.WidgetTokenRequest;
import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.WidgetTokenResponse;
import com.officeplatform.entity.ApiKeyEntity;
import com.officeplatform.repository.ApiKeyRepository;
import com.officeplatform.security.widget.WidgetTokenService;

/**
 * Endpoint público (no requiere X-Api-Key ni autenticación admin) que
 * permite a cualquier aplicativo consumidor obtener un Widget Token a
 * partir de la cédula del usuario y su propia API key.
 *
 * <p><b>Uso:</b>
 * <pre>
 * POST /api/auth/resolve
 * Content-Type: application/json
 *
 * {
 *   "cedula":  "1234567890",
 *   "nombre":  "Juan Pérez",    // opcional
 *   "apiKey":  "opk_..."
 * }
 * </pre>
 *
 * <p><b>Respuesta exitosa:</b>
 * <pre>
 * {
 *   "success": true,
 *   "data": {
 *     "widgetToken": "eyJ...",
 *     "expiresIn": 28800
 *   }
 * }
 * </pre>
 *
 * <p>El {@code widgetToken} debe inyectarse como atributo {@code data-token}
 * en el tag {@code <script>} del widget en el HTML del aplicativo consumidor.
 */
@RestController
@RequestMapping("/api/auth")
public class WidgetAuthController {

    private final ApiKeyRepository    apiKeyRepository;
    private final WidgetTokenService  widgetTokenService;

    public WidgetAuthController(ApiKeyRepository apiKeyRepository,
                                WidgetTokenService widgetTokenService) {
        this.apiKeyRepository   = apiKeyRepository;
        this.widgetTokenService = widgetTokenService;
    }

    /**
     * Valida la API key, y si está activa genera un JWT de corta duración
     * (8 horas) que identifica al usuario por su cédula.
     *
     * @param request body con {@code cedula}, {@code nombre} (opcional) y {@code apiKey}
     * @return {@link WidgetTokenResponse} con el token y segundos de expiración
     */
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<WidgetTokenResponse>> resolve(
            @Valid @RequestBody WidgetTokenRequest request,
            @RequestHeader(value = "X-Api-Key", required = false) String headerApiKey) {

        // La API key puede venir por el header X-Api-Key (recomendado: nunca en el HTML del cliente)
        // o, por retrocompatibilidad, en el body. El header tiene prioridad.
        String apiKey = (headerApiKey != null && !headerApiKey.isBlank())
                ? headerApiKey
                : request.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Se requiere la API key en el header X-Api-Key o en el body");
        }

        // 1. Validar que la API key exista y esté activa
        ApiKeyEntity apiKeyEntity = apiKeyRepository
                .findByApiKeyAndActiveTrue(apiKey)
                .orElseThrow(() -> new com.officeplatform.exception.ApiKeyNotFoundException(-1L) {
                    @Override
                    public String getMessage() {
                        return "La API key proporcionada no es válida o está inactiva";
                    }
                });

        // 2. Generar el widget token
        String userId = request.getResolvedUserId();
        String nombre = request.getResolvedUserName();

        String token = widgetTokenService.generateToken(
                userId,
                nombre,
                apiKeyEntity.getId()
        );

        // 3. Retornar
        WidgetTokenResponse data = new WidgetTokenResponse(token, WidgetTokenService.EXPIRES_IN_SECONDS);

        ApiResponse<WidgetTokenResponse> response = ApiResponse.<WidgetTokenResponse>builder()
                .success(true)
                .message("Widget token generado correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

}
