package com.officeplatform.controller;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.officeplatform.dto.request.CreateFolderRequest;
import com.officeplatform.dto.request.MoveFolderRequest;
import com.officeplatform.dto.request.RenameFolderRequest;
import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.FolderResponse;
import com.officeplatform.entity.FolderEntity;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.folder.FolderService;
import com.officeplatform.service.share.ShareService;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;
    private final ShareService shareService;

    public FolderController(FolderService folderService, ShareService shareService) {
        this.folderService = folderService;
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> create(
            @Valid @RequestBody CreateFolderRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        if (request.getParentId() != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, request.getParentId(), principal);
            if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta contenedora para crear subcarpetas.");
            }
        }

        FolderEntity folder = folderService.createFolder(
                request.getName(), request.getParentId(), principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        ApiResponse<FolderResponse> response = ApiResponse.<FolderResponse>builder()
                .success(true)
                .message("Carpeta creada correctamente")
                .data(toFolderResponse(folder))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderResponse>>> list(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<FolderResponse> folders = folderService.listFolders(parentId, principal.getApiKeyId(), principal.resolveUserId(null), scope).stream()
                .map(this::toFolderResponse)
                .toList();

        // Additive permission filter over the shared space only (does not touch the listing query).
        if ("shared".equalsIgnoreCase(scope)) {
            folders = shareService.filterFoldersByPermissions(folders, principal);
        }

        ApiResponse<List<FolderResponse>> response = ApiResponse.<List<FolderResponse>>builder()
                .success(true)
                .message("Carpetas listadas correctamente")
                .data(folders)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> search(
            @RequestParam("q") String q,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<FolderResponse> folders = folderService
                .searchFolders(principal.getApiKeyId(), q, principal.resolveUserId(null), scope).stream()
                .map(this::toFolderResponse)
                .toList();

        ApiResponse<List<FolderResponse>> response = ApiResponse.<List<FolderResponse>>builder()
                .success(true)
                .message("Búsqueda de carpetas completada")
                .data(folders)
                .build();

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<ApiResponse<FolderResponse>> rename(
            @PathVariable Long id,
            @Valid @RequestBody RenameFolderRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, id, principal);
        if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición para renombrar esta carpeta.");
        }

        FolderEntity folder = folderService.renameFolder(
                id, request.getName(), principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<FolderResponse> response = ApiResponse.<FolderResponse>builder()
                .success(true)
                .message("Carpeta renombrada correctamente")
                .data(toFolderResponse(folder))
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, id, principal);
        if (level == null || level == com.officeplatform.entity.SharePermissionEntity.PermissionLevel.VIEW) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("No tienes permisos de descarga para esta carpeta.");
        }

        FolderEntity folder = folderService.getFolder(id, principal.getApiKeyId());
        byte[] zipBytes = folderService.downloadFolderAsZip(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + folder.getName() + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zipBytes.length)
                .body(new ByteArrayResource(zipBytes));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FolderResponse>> move(
            @PathVariable Long id,
            @RequestBody MoveFolderRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, id, principal);
        if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición para mover esta carpeta.");
        }

        if (request.getParentId() != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel destLevel = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, request.getParentId(), principal);
            if (destLevel != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta de destino.");
            }
        }

        FolderEntity folder = folderService.moveFolder(
                id, request.getParentId(), principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        ApiResponse<FolderResponse> response = ApiResponse.<FolderResponse>builder()
                .success(true)
                .message("Carpeta movida correctamente")
                .data(toFolderResponse(folder))
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, id, principal);
        if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición para eliminar esta carpeta.");
        }

        folderService.deleteFolder(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Carpeta eliminada correctamente")
                .build();

        return ResponseEntity.ok(response);
    }

    private FolderResponse toFolderResponse(FolderEntity entity) {
        return new FolderResponse(
                entity.getId(), entity.getUuid(), entity.getName(), entity.getParentId(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCreatedByName(),
                entity.getCreatedByUserId(), entity.getUpdatedByName(), false);
    }

}
