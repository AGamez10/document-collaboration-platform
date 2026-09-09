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
    private final com.officeplatform.service.notification.NotificationService notificationService;

    public FolderController(FolderService folderService, ShareService shareService,
                            com.officeplatform.service.notification.NotificationService notificationService) {
        this.folderService = folderService;
        this.shareService = shareService;
        this.notificationService = notificationService;
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

        // Entrar a una carpeta ajena avisa a su autor. El servicio aplica una ventana de
        // silencio de 5 minutos: navegar dispara un listado por cada carpeta que se abre.
        if (parentId != null) {
            notifyFolderOpened(parentId, principal);
        }

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

        assertFolderOwnerOrAdmin(id, principal, userId, userName, "renombrar");

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

        assertFolderOwnerOrAdmin(id, principal, userId, userName, "mover");

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

        assertFolderOwnerOrAdmin(id, principal, userId, userName, "eliminar");

        folderService.deleteFolder(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Carpeta movida a la papelera")
                .build();

        return ResponseEntity.ok(response);
    }

    /** Carpetas que esta persona envió a su papelera. */
    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> trash(
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        List<FolderResponse> data = folderService.listTrash(principal.resolveUserId(userId))
                .stream().map(this::toFolderResponse).toList();

        ApiResponse<List<FolderResponse>> response = ApiResponse.<List<FolderResponse>>builder()
                .success(true)
                .message("Carpetas en la papelera listadas correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    /** Devuelve una carpeta desde la papelera a la ubicación que tenía antes de eliminarse. */
    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<FolderResponse>> restore(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        assertFolderOwnerOrAdmin(id, principal, userId, userName, "restaurar");

        FolderEntity folder = folderService.restoreFolder(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<FolderResponse> response = ApiResponse.<FolderResponse>builder()
                .success(true)
                .message("Carpeta restaurada correctamente")
                .data(toFolderResponse(folder))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Elimina definitivamente una carpeta de la papelera.
     *
     * <p>El destinatario de una carpeta compartida solo renuncia a su acceso: destruir la carpeta
     * del autor sería desproporcionado, y negarse con un 403 dejaba la papelera imposible de vaciar.
     */
    @DeleteMapping("/{id}/purge")
    public ResponseEntity<ApiResponse<Void>> purge(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        boolean owner = shareService.isOwnerOrAdmin(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, id, principal,
                userId, userName);

        String message;
        if (owner) {
            folderService.purgeFolder(id, principal.getApiKeyId(),
                    principal.resolveUserId(userId), principal.resolveUserName(userName));
            message = "Carpeta eliminada permanentemente";
        } else {
            shareService.revokeAccessForUser(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, id,
                    principal.resolveUserId(userId));
            message = "Carpeta quitada de tus compartidos";
        }

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();

        return ResponseEntity.ok(response);
    }

    /** Avisa al autor de la carpeta que otra persona la abrió. Silencioso ante fallos. */
    private void notifyFolderOpened(Long folderId, ApiKeyPrincipal principal) {
        try {
            com.officeplatform.entity.FolderEntity folder = folderService.getFolder(folderId, principal.getApiKeyId());
            String owner = (folder.getCreatedByUserId() != null && !folder.getCreatedByUserId().isBlank())
                    ? folder.getCreatedByUserId()
                    : folder.getUserId();
            String actor = principal.resolveUserId(null);
            String actorName = principal.resolveUserName(null);
            String who = (actorName != null && !actorName.isBlank()) ? actorName : actor;
            notificationService.notifyResourceAction(
                    owner, actor, who, folder.getId(), "FOLDER",
                    "Carpeta visualizada",
                    who + " abrió tu carpeta '" + folder.getName() + "'");
        } catch (RuntimeException ignored) {
            // Listar carpetas no puede fallar porque no se pudo notificar.
        }
    }

    private FolderResponse toFolderResponse(FolderEntity entity) {
        return new FolderResponse(
                entity.getId(), entity.getUuid(), entity.getName(), entity.getParentId(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCreatedByName(),
                entity.getCreatedByUserId(), entity.getUpdatedByName(), false);
    }


    /**
     * Destructive and structural operations on a folder stay with its author or a project admin.
     *
     * <p>Deliberately stricter than the EDIT check these endpoints used before: an unrestricted
     * folder in the shared space resolves to EDIT for every member of the project, so gating
     * deletion on EDIT let any member destroy somebody else's folder tree. Editing a document is
     * not the same right as deleting the structure that holds it.
     *
     * <p>Identity is forwarded because a caller using the classic {@code X-Api-Key} header carries
     * its cédula in request parameters rather than on the principal.
     *
     * @param action verb used to build the error message ("eliminar", "renombrar", "mover")
     * @throws ShareAccessDeniedException translated to HTTP 403 by GlobalExceptionHandler
     */
    private void assertFolderOwnerOrAdmin(Long folderId, ApiKeyPrincipal principal,
                                          String userId, String userName, String action) {
        boolean allowed = shareService.isOwnerOrAdmin(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, folderId,
                principal, userId, userName);
        if (!allowed) {
            throw new com.officeplatform.exception.ShareAccessDeniedException(
                    "No tienes permisos para " + action + " esta carpeta, no eres el propietario.");
        }
    }
}
