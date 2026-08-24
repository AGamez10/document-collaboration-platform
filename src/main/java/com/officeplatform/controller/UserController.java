package com.officeplatform.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.KnownUserResponse;
import com.officeplatform.entity.KnownUserEntity;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.user.KnownUserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final KnownUserService knownUserService;

    public UserController(KnownUserService knownUserService) {
        this.knownUserService = knownUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnownUserResponse>>> list(
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<KnownUserResponse> data = knownUserService.listUsers(principal.getApiKeyId()).stream()
                .map(this::toResponse)
                .toList();

        ApiResponse<List<KnownUserResponse>> response = ApiResponse.<List<KnownUserResponse>>builder()
                .success(true)
                .message("Usuarios listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<KnownUserResponse>>> search(
            @RequestParam("q") String q,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<KnownUserResponse> data = knownUserService.searchUsers(principal.getApiKeyId(), q).stream()
                .map(this::toResponse)
                .toList();

        ApiResponse<List<KnownUserResponse>> response = ApiResponse.<List<KnownUserResponse>>builder()
                .success(true)
                .message("Búsqueda de usuarios completada")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    private KnownUserResponse toResponse(KnownUserEntity user) {
        return KnownUserResponse.builder()
                .id(user.getId())
                .apiKeyId(user.getApiKeyId())
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .firstSeenAt(user.getFirstSeenAt())
                .lastSeenAt(user.getLastSeenAt())
                .build();
    }

}
