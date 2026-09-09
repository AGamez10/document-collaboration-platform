package com.officeplatform.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.officeplatform.dto.request.ShareRequest;
import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.SharePermissionResponse;
import com.officeplatform.dto.response.SharedResourceResponse;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.share.ShareService;

@RestController
@RequestMapping("/api")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping("/share")
    public ResponseEntity<ApiResponse<SharePermissionResponse>> share(
            @Valid @RequestBody ShareRequest request,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        SharePermissionResponse data = shareService.share(request, principal);

        ApiResponse<SharePermissionResponse> response = ApiResponse.<SharePermissionResponse>builder()
                .success(true)
                .message("Recurso compartido correctamente")
                .data(data)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/share/{resourceType}/{resourceId}")
    public ResponseEntity<ApiResponse<List<SharePermissionResponse>>> listResourcePermissions(
            @PathVariable String resourceType,
            @PathVariable Long resourceId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<SharePermissionResponse> data =
                shareService.listResourcePermissions(parseResourceType(resourceType), resourceId, principal);

        ApiResponse<List<SharePermissionResponse>> response = ApiResponse.<List<SharePermissionResponse>>builder()
                .success(true)
                .message("Permisos del recurso listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/share/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable Long permissionId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        shareService.revoke(permissionId, principal);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Permiso revocado correctamente")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Lo que le compartieron a esta persona y ella descartó de su vista.
     *
     * <p>Va aparte de {@code /api/files/trash} porque son cosas distintas: esa papelera lista
     * archivos que el autor eliminó del proyecto, y ésta, vínculos que el destinatario ocultó sin
     * tocar el original. El gestor las muestra juntas, pero restaurar cada una hace algo distinto.
     */
    @GetMapping("/trashed-with-me")
    public ResponseEntity<ApiResponse<List<SharedResourceResponse>>> trashedWithMe(
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<SharedResourceResponse> data = shareService.trashedSharedWithMe(principal);

        ApiResponse<List<SharedResourceResponse>> response = ApiResponse.<List<SharedResourceResponse>>builder()
                .success(true)
                .message("Compartidos en tu papelera listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<ApiResponse<List<SharedResourceResponse>>> sharedWithMe(
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<SharedResourceResponse> data = shareService.sharedWithMe(principal);

        ApiResponse<List<SharedResourceResponse>> response = ApiResponse.<List<SharedResourceResponse>>builder()
                .success(true)
                .message("Recursos compartidos conmigo listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared-with-project")
    public ResponseEntity<ApiResponse<List<SharedResourceResponse>>> sharedWithProject(
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<SharedResourceResponse> data = shareService.sharedWithProject(principal);

        ApiResponse<List<SharedResourceResponse>> response = ApiResponse.<List<SharedResourceResponse>>builder()
                .success(true)
                .message("Recursos compartidos con el proyecto listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    private ResourceType parseResourceType(String raw) {
        try {
            return ResourceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("resourceType inválido: debe ser FILE o FOLDER");
        }
    }

}
