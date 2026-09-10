package com.officeplatform.controller;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import com.officeplatform.dto.request.BackupConfigRequest;
import com.officeplatform.dto.request.CreateApiKeyRequest;
import com.officeplatform.dto.request.UpdateQuotaRequest;
import com.officeplatform.dto.request.UpdateApiKeyStatusRequest;
import com.officeplatform.dto.request.UpdateUserRoleRequest;
import com.officeplatform.dto.response.ActivityLogResponse;
import com.officeplatform.dto.response.AdminFileResponse;
import com.officeplatform.dto.response.ApiKeyCreatedResponse;
import com.officeplatform.dto.response.ApiKeyResponse;
import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.BackupConfigResponse;
import com.officeplatform.dto.response.BackupInfoResponse;
import com.officeplatform.dto.response.BackupRestoreResponse;
import com.officeplatform.dto.response.DashboardResponse;
import com.officeplatform.dto.response.EditorSessionResponse;
import com.officeplatform.dto.response.KnownUserResponse;
import com.officeplatform.dto.response.OnlyOfficeStatusResponse;
import com.officeplatform.dto.response.PagedResponse;
import com.officeplatform.dto.response.ProjectStorageResponse;
import com.officeplatform.service.admin.AdminService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final int DEFAULT_ACTIVITY_LOG_PAGE_SIZE = 20;

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        DashboardResponse data = adminService.getDashboard();

        ApiResponse<DashboardResponse> response = ApiResponse.<DashboardResponse>builder()
                .success(true)
                .message("Panel de administración obtenido correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> listApiKeys() {
        List<ApiKeyResponse> data = adminService.listApiKeys();

        ApiResponse<List<ApiKeyResponse>> response = ApiResponse.<List<ApiKeyResponse>>builder()
                .success(true)
                .message("API keys listadas correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiResponse<ApiKeyCreatedResponse>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request) {

        ApiKeyCreatedResponse data = adminService.createApiKey(request);

        ApiResponse<ApiKeyCreatedResponse> response = ApiResponse.<ApiKeyCreatedResponse>builder()
                .success(true)
                .message("API key creada correctamente. Guardala ahora: no volverá a mostrarse completa.")
                .data(data)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/api-keys/{id}")
    public ResponseEntity<ApiResponse<ApiKeyResponse>> updateApiKeyStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApiKeyStatusRequest request) {

        ApiKeyResponse data = adminService.updateApiKeyStatus(id, request.getActive());

        ApiResponse<ApiKeyResponse> response = ApiResponse.<ApiKeyResponse>builder()
                .success(true)
                .message("Estado de la API key actualizado correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteApiKey(@PathVariable Long id) {
        adminService.deleteApiKey(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("API key eliminada permanentemente")
                .build();

        return ResponseEntity.ok(response);
    }

    // ── Cuotas de almacenamiento ────────────────────────────────────────────

    /** Consumo y tope de cada proyecto, ordenado por lo que mas ocupa. */
    @GetMapping("/storage/summary")
    public ResponseEntity<ApiResponse<List<ProjectStorageResponse>>> storageSummary() {
        List<ProjectStorageResponse> data = adminService.getStorageSummary();

        ApiResponse<List<ProjectStorageResponse>> response = ApiResponse.<List<ProjectStorageResponse>>builder()
                .success(true)
                .message("Consumo de almacenamiento por proyecto")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Asigna o quita el tope de un proyecto.
     *
     * <p>Se recibe en gigabytes porque es la unidad en la que piensa un administrador. Enviar
     * {@code quotaGb} nulo deja el proyecto sin limite, que es como funcionan los que nadie
     * configuro; cero seria bloquearlo entero y no es lo que nadie quiere decir.
     */
    @PatchMapping("/projects/{id}/quota")
    public ResponseEntity<ApiResponse<ProjectStorageResponse>> updateQuota(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuotaRequest request) {

        ProjectStorageResponse data = adminService.updateQuota(id, request.getQuotaGb());

        ApiResponse<ProjectStorageResponse> response = ApiResponse.<ProjectStorageResponse>builder()
                .success(true)
                .message(request.getQuotaGb() == null
                        ? "Proyecto sin limite de almacenamiento"
                        : "Cuota actualizada a " + data.getFormattedQuota())
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/activity-log")
    public ResponseEntity<ApiResponse<PagedResponse<ActivityLogResponse>>> activityLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_ACTIVITY_LOG_PAGE_SIZE) int size,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Long folderId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        PagedResponse<ActivityLogResponse> data =
                adminService.getActivityLog(pageable, dateFrom, dateTo, apiKeyId, action, userId, folderId);

        ApiResponse<PagedResponse<ActivityLogResponse>> response = ApiResponse.<PagedResponse<ActivityLogResponse>>builder()
                .success(true)
                .message("Registro de actividad obtenido correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/files")
    public ResponseEntity<ApiResponse<List<AdminFileResponse>>> listFiles(
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(defaultValue = "false") boolean trashed) {

        List<AdminFileResponse> data = adminService.listFiles(apiKeyId, trashed);

        ApiResponse<List<AdminFileResponse>> response = ApiResponse.<List<AdminFileResponse>>builder()
                .success(true)
                .message("Archivos listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Devuelve un archivo a la papelera de su dueño. La restauración final la hace él.
     *
     * <p>El mensaje dice lo que realmente pasa: antes anunciaba "restaurado a su ubicación
     * original" mientras empujaba el documento al espacio activo de alguien que no lo había pedido.
     */
    @PostMapping("/files/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreFile(@PathVariable Long id) {
        adminService.restoreFile(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Archivo devuelto a la papelera del usuario. Él decide la restauración final.")
                .build();

        return ResponseEntity.ok(response);
    }

    /** Devuelve una carpeta a la papelera de su dueño. La restauración final la hace él. */
    @PostMapping("/folders/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreFolder(@PathVariable Long id) {
        adminService.restoreFolder(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Carpeta devuelta a la papelera del usuario. Él decide la restauración final.")
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/files/{id}/purge")
    public ResponseEntity<ApiResponse<Void>> purgeFile(@PathVariable Long id) {
        adminService.purgeFile(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Archivo eliminado permanentemente")
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/onlyoffice/status")
    public ResponseEntity<ApiResponse<OnlyOfficeStatusResponse>> onlyOfficeStatus() {
        OnlyOfficeStatusResponse data = adminService.getOnlyOfficeStatus();

        ApiResponse<OnlyOfficeStatusResponse> response = ApiResponse.<OnlyOfficeStatusResponse>builder()
                .success(true)
                .message("Estado de OnlyOffice obtenido correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/editor-sessions")
    public ResponseEntity<ApiResponse<List<EditorSessionResponse>>> editorSessions(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long apiKeyId) {

        List<EditorSessionResponse> data = adminService.getEditorSessions(active, apiKeyId);

        ApiResponse<List<EditorSessionResponse>> response = ApiResponse.<List<EditorSessionResponse>>builder()
                .success(true)
                .message("Sesiones de edición listadas correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/editor-sessions/{id}/close")
    public ResponseEntity<ApiResponse<Void>> forceCloseSession(@PathVariable Long id) {
        adminService.forceCloseSession(id);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Sesión cerrada correctamente")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/editor-sessions/close-all")
    public ResponseEntity<ApiResponse<Integer>> forceCloseAllSessions() {
        int closedCount = adminService.forceCloseAllSessions();

        ApiResponse<Integer> response = ApiResponse.<Integer>builder()
                .success(true)
                .message(closedCount + " sesiones activas cerradas correctamente")
                .data(closedCount)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<KnownUserResponse>>> listUsers(
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) String search) {

        List<KnownUserResponse> data = adminService.listUsers(apiKeyId, search);

        ApiResponse<List<KnownUserResponse>> response = ApiResponse.<List<KnownUserResponse>>builder()
                .success(true)
                .message("Usuarios listados correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/users/{knownUserId}/role")
    public ResponseEntity<ApiResponse<KnownUserResponse>> updateUserRole(
            @PathVariable Long knownUserId,
            @Valid @RequestBody UpdateUserRoleRequest request) {

        KnownUserResponse data = adminService.updateUserRole(knownUserId, request.getRole());

        ApiResponse<KnownUserResponse> response = ApiResponse.<KnownUserResponse>builder()
                .success(true)
                .message("Rol del usuario actualizado correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/backups")
    public ResponseEntity<ApiResponse<List<BackupInfoResponse>>> listBackups() {
        List<BackupInfoResponse> data = adminService.listBackups();

        ApiResponse<List<BackupInfoResponse>> response = ApiResponse.<List<BackupInfoResponse>>builder()
                .success(true)
                .message("Copias de seguridad listadas correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Comprueba que el respaldo externo funcione de verdad.
     *
     * <p>Escribe y borra un archivo en el destino configurado. Que la ruta exista no alcanza: un
     * recurso de red montado en solo lectura acepta la comprobacion de existencia y despues falla
     * al copiar, y eso se descubriria el dia que hiciera falta el respaldo.
     */
    @PostMapping("/backups/test-remote")
    public ResponseEntity<ApiResponse<com.officeplatform.dto.response.PathValidationResponse>> testRemoteBackup() {
        com.officeplatform.dto.response.PathValidationResponse data = adminService.testRemoteBackup();

        ApiResponse<com.officeplatform.dto.response.PathValidationResponse> response =
                ApiResponse.<com.officeplatform.dto.response.PathValidationResponse>builder()
                        .success(data.isValid())
                        .message(data.getMessage())
                        .data(data)
                        .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/backups/create")
    public ResponseEntity<ApiResponse<BackupInfoResponse>> createBackup(
            @RequestParam(defaultValue = "MANUAL") String type) {

        BackupInfoResponse data = adminService.createBackup(type);

        ApiResponse<BackupInfoResponse> response = ApiResponse.<BackupInfoResponse>builder()
                .success(true)
                .message("Copia de seguridad generada con éxito")
                .data(data)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Entrega el paquete de respaldo.
     *
     * <p>El panel lo pide por {@code fetch} con la cabecera Basic, porque el endpoint exige
     * ROLE_ADMIN y un enlace nativo del navegador viaja sin credenciales: Spring respondía 401 y
     * Chrome cancelaba la descarga sin explicar por qué. Se anuncia el tipo real y el tamaño para
     * que la barra de progreso funcione; si el archivo no se puede medir, se omite el largo antes
     * que fallar la descarga entera.
     */
    @GetMapping("/backups/{fileName}/download")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String fileName) {
        InputStream stream = adminService.getBackupStream(fileName);
        InputStreamResource resource = new InputStreamResource(stream);
        long size = adminService.getBackupSize(fileName);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/zip"));
        if (size >= 0) {
            builder.contentLength(size);
        }
        return builder.body(resource);
    }

    /**
     * Reinstates a package already stored in the backup directory.
     *
     * <p>Synchronises rather than wipes: rows already present are refreshed and missing ones are
     * recreated, so restoring over a live database does not destroy work done since the snapshot.
     * The response reports what was created versus updated, plus any binary that could not be
     * returned to object storage.
     */
    @PostMapping("/backups/{fileName}/restore")
    public ResponseEntity<ApiResponse<BackupRestoreResponse>> restoreBackup(@PathVariable String fileName) {
        BackupRestoreResponse data = adminService.restoreBackup(fileName);

        ApiResponse<BackupRestoreResponse> response = ApiResponse.<BackupRestoreResponse>builder()
                .success(true)
                .message("Copia de seguridad restaurada correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    /** Stores an externally supplied package in the backup directory without restoring it. */
    @PostMapping("/backups/upload")
    public ResponseEntity<ApiResponse<BackupInfoResponse>> uploadBackup(
            @RequestParam("file") MultipartFile file) throws java.io.IOException {

        BackupInfoResponse data = adminService.storeUploadedBackup(
                file.getOriginalFilename(), file.getInputStream());

        ApiResponse<BackupInfoResponse> response = ApiResponse.<BackupInfoResponse>builder()
                .success(true)
                .message("Respaldo externo cargado correctamente")
                .data(data)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Uploads and immediately restores an external package, for a server installed from scratch. */
    @PostMapping("/backups/upload-and-restore")
    public ResponseEntity<ApiResponse<BackupRestoreResponse>> uploadAndRestoreBackup(
            @RequestParam("file") MultipartFile file) throws java.io.IOException {

        BackupRestoreResponse data = adminService.restoreFromUpload(
                file.getOriginalFilename(), file.getInputStream());

        ApiResponse<BackupRestoreResponse> response = ApiResponse.<BackupRestoreResponse>builder()
                .success(true)
                .message("Respaldo externo restaurado correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/backups/{fileName}")
    public ResponseEntity<ApiResponse<Void>> deleteBackup(@PathVariable String fileName) {
        adminService.deleteBackup(fileName);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Copia de seguridad eliminada")
                .build();

        return ResponseEntity.ok(response);
    }

    /** Comprueba una ruta de destino antes de guardarla, para no descubrir el error en el
     *  próximo respaldo automático. */
    @PostMapping("/backups/config/validate-path")
    public ResponseEntity<ApiResponse<com.officeplatform.dto.response.PathValidationResponse>> validateBackupPath(
            @Valid @RequestBody com.officeplatform.dto.request.ValidatePathRequest request) {

        com.officeplatform.dto.response.PathValidationResponse data =
                adminService.validateBackupPath(request.getPath());

        ApiResponse<com.officeplatform.dto.response.PathValidationResponse> response =
                ApiResponse.<com.officeplatform.dto.response.PathValidationResponse>builder()
                        .success(true)
                        .message(data.getMessage())
                        .data(data)
                        .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/backups/config")
    public ResponseEntity<ApiResponse<BackupConfigResponse>> getBackupConfig() {
        BackupConfigResponse data = adminService.getBackupConfig();

        ApiResponse<BackupConfigResponse> response = ApiResponse.<BackupConfigResponse>builder()
                .success(true)
                .message("Configuración de copias de seguridad obtenida")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/backups/config")
    public ResponseEntity<ApiResponse<BackupConfigResponse>> updateBackupConfig(
            @Valid @RequestBody BackupConfigRequest request) {

        BackupConfigResponse data = adminService.updateBackupConfig(request);

        ApiResponse<BackupConfigResponse> response = ApiResponse.<BackupConfigResponse>builder()
                .success(true)
                .message("Configuración de copias de seguridad actualizada")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

}
