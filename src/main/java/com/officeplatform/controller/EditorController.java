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
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        EditorConfigResponse config = editorService.getEditorConfig(id, principal,
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<EditorConfigResponse> response = ApiResponse.<EditorConfigResponse>builder()
                .success(true)
                .message("Configuración del editor generada correctamente")
                .data(config)
                .build();

        return ResponseEntity.ok(response);
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

}
