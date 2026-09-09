package com.officeplatform.controller;

import java.util.List;

import java.io.InputStream;

import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import com.officeplatform.dto.request.MoveFileRequest;
import com.officeplatform.dto.request.RenameFileRequest;
import com.officeplatform.dto.request.UploadFileRequest;
import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.FileResponse;
import com.officeplatform.dto.response.UploadResponse;
import com.officeplatform.entity.FileEntity;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.file.FileService;
import com.officeplatform.service.folder.FolderService;
import com.officeplatform.service.share.ShareService;
import com.officeplatform.util.MimeUtils;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FolderService folderService;
    private final ShareService shareService;
    private final com.officeplatform.service.notification.NotificationService notificationService;

    public FileController(FileService fileService, FolderService folderService, ShareService shareService,
                          com.officeplatform.service.notification.NotificationService notificationService) {
        this.fileService = fileService;
        this.folderService = folderService;
        this.shareService = shareService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileResponse>>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {
        List<FileResponse> files = fileService.listFiles(principal.getApiKeyId(), folderId, principal.resolveUserId(null), scope).stream()
                .map(this::toFileResponse)
                .toList();

        // Additive permission filter over the shared space only (does not touch the listing query).
        if ("shared".equalsIgnoreCase(scope)) {
            files = shareService.filterFilesByPermissions(files, principal);
        }

        ApiResponse<List<FileResponse>> response = ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Archivos listados correctamente")
                .data(files)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<FileResponse>>> listAll(
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {
        List<FileResponse> files = fileService.listAllFiles(principal.getApiKeyId(), principal.resolveUserId(null), scope).stream()
                .map(this::toFileResponse)
                .toList();

        ApiResponse<List<FileResponse>> response = ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Archivos listados correctamente")
                .data(files)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FileResponse>>> search(
            @RequestParam("q") String q,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {
        List<FileResponse> files = fileService
                .searchFiles(principal.getApiKeyId(), q, principal.resolveUserId(null), scope).stream()
                .map(this::toFileResponse)
                .toList();

        ApiResponse<List<FileResponse>> response = ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Búsqueda de archivos completada")
                .data(files)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<FileResponse>>> listTrash(
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {
        List<FileResponse> files = fileService.listTrash(
                        principal.getApiKeyId(),
                        principal.resolveUserId(null),
                        principal.resolveUserName(null),
                        scope).stream()
                .map(this::toFileResponse)
                .toList();

        ApiResponse<List<FileResponse>> response = ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Papelera listada correctamente")
                .data(files)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @Valid @RequestPart("request") UploadFileRequest request,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        if (folderId != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, folderId, principal);
            if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta contenedora para subir archivos.");
            }
        }

        FileEntity fileEntity = fileService.uploadFile(file, request, principal.getApiKeyId(), folderId,
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        UploadResponse uploadResponse = new UploadResponse(
                fileEntity.getId(),
                fileEntity.getUuid(),
                fileEntity.getOriginalFileName(),
                fileEntity.getMimeType(),
                fileEntity.getSize(),
                fileEntity.getCreatedAt());

        ApiResponse<UploadResponse> response = ApiResponse.<UploadResponse>builder()
                .success(true)
                .message("Archivo subido correctamente")
                .data(uploadResponse)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/new")
    public ResponseEntity<ApiResponse<UploadResponse>> createBlank(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        if (folderId != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, folderId, principal);
            if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta contenedora para crear archivos.");
            }
        }

        FileEntity fileEntity = fileService.createBlankFile(principal.getApiKeyId(), folderId,
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        UploadResponse uploadResponse = new UploadResponse(
                fileEntity.getId(),
                fileEntity.getUuid(),
                fileEntity.getOriginalFileName(),
                fileEntity.getMimeType(),
                fileEntity.getSize(),
                fileEntity.getCreatedAt());

        ApiResponse<UploadResponse> response = ApiResponse.<UploadResponse>builder()
                .success(true)
                .message("Documento en blanco creado correctamente")
                .data(uploadResponse)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/new/spreadsheet")
    public ResponseEntity<ApiResponse<UploadResponse>> createBlankSpreadsheet(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        if (folderId != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, folderId, principal);
            if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta contenedora para crear archivos.");
            }
        }

        FileEntity fileEntity = fileService.createBlankSpreadsheet(principal.getApiKeyId(), folderId,
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        UploadResponse uploadResponse = new UploadResponse(
                fileEntity.getId(),
                fileEntity.getUuid(),
                fileEntity.getOriginalFileName(),
                fileEntity.getMimeType(),
                fileEntity.getSize(),
                fileEntity.getCreatedAt());

        ApiResponse<UploadResponse> response = ApiResponse.<UploadResponse>builder()
                .success(true)
                .message("Hoja de cálculo en blanco creada correctamente")
                .data(uploadResponse)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/new/presentation")
    public ResponseEntity<ApiResponse<UploadResponse>> createBlankPresentation(
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        if (folderId != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, folderId, principal);
            if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta contenedora para crear archivos.");
            }
        }

        FileEntity fileEntity = fileService.createBlankPresentation(principal.getApiKeyId(), folderId,
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        UploadResponse uploadResponse = new UploadResponse(
                fileEntity.getId(),
                fileEntity.getUuid(),
                fileEntity.getOriginalFileName(),
                fileEntity.getMimeType(),
                fileEntity.getSize(),
                fileEntity.getCreatedAt());

        ApiResponse<UploadResponse> response = ApiResponse.<UploadResponse>builder()
                .success(true)
                .message("Presentación en blanco creada correctamente")
                .data(uploadResponse)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/download/{uuid}")
    public ResponseEntity<InputStreamResource> downloadByUuid(@PathVariable String uuid) {
        FileEntity fileEntity = fileService.getFileByUuid(uuid);
        InputStream inputStream = fileService.downloadByUuid(uuid);
        // El tipo se deriva de la extensión original: un .xlsm servido con el MIME genérico de
        // Excel llega a Excel de escritorio como un .xls antiguo y pierde el proyecto de macros.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileEntity.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(MimeUtils.contentTypeForFileName(
                        fileEntity.getOriginalFileName(), fileEntity.getMimeType())))
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, id, principal);
        if (level == null || level == com.officeplatform.entity.SharePermissionEntity.PermissionLevel.VIEW) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("No tienes permisos de descarga para este archivo.");
        }

        // Identity is passed so a private file owned by this caller resolves even when it was
        // created from another consumer application ("Mis archivos" is decentralized).
        FileEntity fileEntity = fileService.getFile(id, principal.getApiKeyId(),
                principal.resolveUserId(userId));
        InputStream inputStream = fileService.downloadFile(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileEntity.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(MimeUtils.contentTypeForFileName(
                        fileEntity.getOriginalFileName(), fileEntity.getMimeType())))
                .body(new InputStreamResource(inputStream));
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<ApiResponse<FileResponse>> rename(
            @PathVariable Long id,
            @Valid @RequestBody RenameFileRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, id, principal);
        if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición para renombrar este archivo.");
        }

        FileEntity fileEntity = fileService.renameFile(
                id, principal.getApiKeyId(), request.getOriginalFileName(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        notifyOwnerOfChange(fileEntity, principal, userId, userName, "renombró");

        ApiResponse<FileResponse> response = ApiResponse.<FileResponse>builder()
                .success(true)
                .message("Archivo renombrado correctamente")
                .data(toFileResponse(fileEntity))
                .build();

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<ApiResponse<FileResponse>> move(
            @PathVariable Long id,
            @RequestBody MoveFileRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        com.officeplatform.entity.SharePermissionEntity.PermissionLevel level = shareService.getEffectivePermission(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, id, principal);
        if (level != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
            throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición para mover este archivo.");
        }

        if (request.getFolderId() != null) {
            com.officeplatform.entity.SharePermissionEntity.PermissionLevel destLevel = shareService.getEffectivePermission(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FOLDER, request.getFolderId(), principal);
            if (destLevel != com.officeplatform.entity.SharePermissionEntity.PermissionLevel.EDIT) {
                throw new com.officeplatform.exception.ShareAccessDeniedException("Se requieren permisos de edición en la carpeta de destino.");
            }
        }

        FileEntity fileEntity = folderService.moveFile(
                id, request.getFolderId(), principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName), scope);

        notifyOwnerOfChange(fileEntity, principal, userId, userName, "movió");

        ApiResponse<FileResponse> response = ApiResponse.<FileResponse>builder()
                .success(true)
                .message("Archivo movido correctamente")
                .data(toFileResponse(fileEntity))
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        assertOwnerOrAdmin(id, principal, userId, userName, "eliminar");

        fileService.softDeleteFile(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Archivo movido a la papelera")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<FileResponse>> restore(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        assertOwnerOrAdmin(id, principal, userId, userName, "restaurar");

        FileEntity fileEntity = fileService.restoreFile(id, principal.getApiKeyId(),
                principal.resolveUserId(userId), principal.resolveUserName(userName));

        ApiResponse<FileResponse> response = ApiResponse.<FileResponse>builder()
                .success(true)
                .message("Archivo restaurado correctamente")
                .data(toFileResponse(fileEntity))
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/purge")
    public ResponseEntity<ApiResponse<Void>> purge(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        // Se resuelve primero para que un id inexistente responda 404 y no "no eres el propietario".
        fileService.getFileIncludingTrashed(id, principal.getApiKeyId(), principal.resolveUserId(userId));

        // Vaciar la papelera no puede depender de ser el autor. Antes esto exigía ser dueño o
        // administrador y devolvía 403 sobre cada archivo que a la persona le habían compartido:
        // la papelera quedaba con "X con error" y era imposible de limpiar. El autor borra de
        // verdad; el destinatario solo renuncia a su propio acceso y el original queda intacto.
        boolean owner = shareService.isOwnerOrAdmin(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, id, principal,
                userId, userName);

        String message;
        if (owner) {
            fileService.purgeFile(id, principal.getApiKeyId(),
                    principal.resolveUserId(userId), principal.resolveUserName(userName));
            message = "Archivo eliminado permanentemente";
        } else {
            shareService.revokeAccessForUser(
                    com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, id,
                    principal.resolveUserId(userId));
            message = "Archivo quitado de tus compartidos";
        }

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Destructive operations stay with the author or a project admin.
     *
     * <p>Deliberately stricter than the EDIT check used by rename/move: an unrestricted file in
     * the shared space resolves to EDIT for every member of the project, so gating deletion on
     * EDIT let any member delete anybody else's work.
     *
     * @param action verb used to build the error message ("eliminar", "restaurar", ...)
     * @throws ShareAccessDeniedException translated to HTTP 403 by GlobalExceptionHandler
     */
    private void assertOwnerOrAdmin(Long fileId, ApiKeyPrincipal principal, String action) {
        assertOwnerOrAdmin(fileId, principal, null, null, action);
    }

    /**
     * Destructive operations stay with the author or a project admin, judged on the identity the
     * request actually carries.
     *
     * <p>The identity has to be forwarded: a widget using the classic {@code X-Api-Key} header has
     * no cédula on the principal and sends it as a request parameter, so checking the principal
     * alone rejected the very author of the file with "no eres el propietario".
     *
     * @param action verb used to build the error message ("eliminar", "restaurar", ...)
     * @throws ShareAccessDeniedException translated to HTTP 403 by GlobalExceptionHandler
     */
    private void assertOwnerOrAdmin(Long fileId, ApiKeyPrincipal principal,
                                    String userId, String userName, String action) {
        // Resolved first so a file that does not exist answers 404 instead of "no eres el
        // propietario", which would be misleading for an already purged file. Identity is passed
        // so a private file this caller owns under another project resolves too.
        fileService.getFileIncludingTrashed(fileId, principal.getApiKeyId(),
                principal.resolveUserId(userId));

        boolean allowed = shareService.isOwnerOrAdmin(
                com.officeplatform.entity.SharePermissionEntity.ResourceType.FILE, fileId, principal,
                userId, userName);
        if (!allowed) {
            throw new com.officeplatform.exception.ShareAccessDeniedException(
                    "No tienes permisos para " + action + " este archivo, no eres el propietario.");
        }
    }

    /**
     * Avisa al autor cuando otra persona altera su documento.
     *
     * <p>Silencioso ante fallos: mover o renombrar debe completarse igual aunque la notificación
     * no pueda registrarse. El servicio ya ignora el caso de actuar sobre lo propio.
     */
    private void notifyOwnerOfChange(FileEntity file, ApiKeyPrincipal principal,
                                     String userId, String userName, String verb) {
        try {
            String actor = principal.resolveUserId(userId);
            String actorName = principal.resolveUserName(userName);
            String who = (actorName != null && !actorName.isBlank()) ? actorName : actor;
            notificationService.notifyResourceAction(
                    file.getCreatedByUserId() != null ? file.getCreatedByUserId() : file.getUserId(),
                    actor,
                    who,
                    file.getId(),
                    "FILE",
                    "Documento modificado",
                    who + " " + verb + " tu documento '" + file.getOriginalFileName() + "'");
        } catch (RuntimeException ignored) {
            // Información secundaria: no interrumpe la operación que la disparó.
        }
    }

    private FileResponse toFileResponse(FileEntity fileEntity) {
        return new FileResponse(
                fileEntity.getId(),
                fileEntity.getUuid(),
                fileEntity.getOriginalFileName(),
                fileEntity.getMimeType(),
                fileEntity.getSize(),
                fileEntity.getFolderId(),
                fileEntity.getCreatedAt(),
                fileEntity.getUpdatedAt(),
                fileEntity.getDeletedAt(),
                fileEntity.getCreatedByName(),
                fileEntity.getCreatedByUserId(),
                fileEntity.getUpdatedByName(),
                false);
    }

}
