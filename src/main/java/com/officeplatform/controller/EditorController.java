package com.officeplatform.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;

import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.EditorConfigResponse;
import com.officeplatform.onlyoffice.dto.OnlyOfficeCallbackRequest;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.editor.EditorService;
import com.officeplatform.util.UrlUtils;

@RestController
public class EditorController {

    private static final String PROXY_PREFIX = "/api/editor/proxy/";

    private final EditorService editorService;
    private final RestTemplate restTemplate;
    private final String documentServerUrl;

    public EditorController(
            EditorService editorService,
            RestTemplate restTemplate,
            @Value("${office-platform.onlyoffice.document-server-url}") String documentServerUrl) {
        this.editorService = editorService;
        this.restTemplate = restTemplate;
        this.documentServerUrl = documentServerUrl;
    }

    @GetMapping("/api/editor/{id}")
    public ResponseEntity<ApiResponse<EditorConfigResponse>> configure(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            HttpServletRequest request) {

        EditorConfigResponse config = editorService.getEditorConfig(id, principal,
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        if (config != null && config.getDocumentServer() != null && request != null) {
            String clientHost = resolveClientFacingHost(request);
            if (clientHost != null && !clientHost.equalsIgnoreCase("localhost") && !clientHost.equals("127.0.0.1")) {
                try {
                    java.net.URI currentUri = new java.net.URI(config.getDocumentServer());
                    if ("localhost".equalsIgnoreCase(currentUri.getHost()) || "127.0.0.1".equals(currentUri.getHost())) {
                        java.net.URI adaptedUri = new java.net.URI(
                                currentUri.getScheme(),
                                currentUri.getUserInfo(),
                                clientHost,
                                currentUri.getPort(),
                                currentUri.getPath(),
                                currentUri.getQuery(),
                                currentUri.getFragment()
                        );
                        config.setDocumentServer(adaptedUri.toString().replaceAll("/$", ""));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        ApiResponse<EditorConfigResponse> response = ApiResponse.<EditorConfigResponse>builder()
                .success(true)
                .message("Configuración del editor generada correctamente")
                .data(config)
                .build();

        return ResponseEntity.ok(response);
    }

    private String resolveClientFacingHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            return forwardedHost.split(",")[0].trim().split(":")[0].trim();
        }
        String hostHeader = request.getHeader("Host");
        if (hostHeader != null && !hostHeader.isBlank()) {
            return hostHeader.split(":")[0].trim();
        }
        return request.getServerName();
    }

    @PostMapping("/api/editor/{id}/close")
    public ResponseEntity<ApiResponse<Void>> closeSession(
            @PathVariable Long id,
            @RequestParam String key,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        editorService.closeSession(id, key, principal.getApiKeyId());

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Sesión de edición cerrada")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/onlyoffice/callback")
    public ResponseEntity<Map<String, Integer>> callback(@RequestBody OnlyOfficeCallbackRequest callback) {
        editorService.processCallback(callback);
        return ResponseEntity.ok(Map.of("error", 0));
    }

    @GetMapping("/api/editor/proxy/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String path = requestUri.substring(requestUri.indexOf(PROXY_PREFIX) + PROXY_PREFIX.length());

        String targetUrl = UrlUtils.joinPath(documentServerUrl, path);

        ResponseEntity<byte[]> proxied = restTemplate.getForEntity(targetUrl, byte[].class);

        HttpHeaders headers = new HttpHeaders();
        if (proxied.getHeaders().getContentType() != null) {
            headers.setContentType(proxied.getHeaders().getContentType());
        }

        return new ResponseEntity<>(proxied.getBody(), headers, proxied.getStatusCode());
    }


    /**
     * Keeps an editing session alive while the user is actually working on it.
     *
     * <p>Answers 200 with {@code false} when the session is gone instead of 404: the widget uses
     * that to stop its timer, and a missing session is an expected outcome here, not an error.
     */
    @PostMapping("/api/editor/sessions/{sessionId}/heartbeat")
    public ResponseEntity<ApiResponse<Boolean>> heartbeat(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        boolean alive = editorService.heartbeat(sessionId, principal);

        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .success(true)
                .message(alive ? "Sesión activa" : "La sesión ya no está abierta")
                .data(alive)
                .build();

        return ResponseEntity.ok(response);
    }

}
