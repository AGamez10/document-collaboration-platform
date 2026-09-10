package com.officeplatform.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.ErrorResponse;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileNotFoundException(
            FileNotFoundException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("FILE_NOT_FOUND")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("El archivo no existe o no pertenece a tu proyecto")
            .metadata(Map.of("error", error))
            .build();

        log.warn("FileNotFoundException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFolderNotFoundException(
            FolderNotFoundException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("FOLDER_NOT_FOUND")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("La carpeta no existe o no pertenece a tu proyecto")
            .metadata(Map.of("error", error))
            .build();

        log.warn("FolderNotFoundException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiKeyNotFoundException(
            ApiKeyNotFoundException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("API_KEY_NOT_FOUND")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("La API key no existe")
            .metadata(Map.of("error", error))
            .build();

        log.warn("ApiKeyNotFoundException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedFileTypeException(
            UnsupportedFileTypeException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("UNSUPPORTED_FILE_TYPE")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .metadata(Map.of("error", error))
            .build();

        log.warn("Carga rechazada por tipo de archivo en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    /**
     * Cuota agotada: 413 y no 500.
     *
     * <p>El servidor funciona perfectamente; lo que no entra es el archivo. Un 500 invitaria a
     * reintentar indefinidamente algo que nunca va a pasar hasta que un administrador amplie el
     * tope, y ocultaria una decision administrativa detras de un error tecnico.
     */
    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageQuotaExceeded(
            StorageQuotaExceededException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("STORAGE_QUOTA_EXCEEDED")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .metadata(java.util.Map.of("error", error))
                .build());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageException(
            StorageException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("STORAGE_ERROR")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("Error al procesar el archivo en almacenamiento")
            .metadata(Map.of("error", error))
            .build();

        log.error("StorageException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(OnlyOfficeException.class)
    public ResponseEntity<ApiResponse<Void>> handleOnlyOfficeException(
            OnlyOfficeException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("ONLYOFFICE_ERROR")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("Error al comunicarse con OnlyOffice Document Server")
            .metadata(Map.of("error", error))
            .build();

        log.error("OnlyOfficeException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(ShareAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleShareAccessDeniedException(
            ShareAccessDeniedException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("SHARE_ACCESS_DENIED")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message(ex.getMessage())
            .metadata(Map.of("error", error))
            .build();

        log.warn("ShareAccessDeniedException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(SharePermissionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSharePermissionNotFoundException(
            SharePermissionNotFoundException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("SHARE_PERMISSION_NOT_FOUND")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("El permiso de compartición no existe")
            .metadata(Map.of("error", error))
            .build();

        log.warn("SharePermissionNotFoundException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("BAD_REQUEST")
            .details(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            // El mensaje de la excepción explica QUÉ está mal ("debe combinar letras y
            // números"). Reemplazarlo por "Solicitud inválida" deja al usuario adivinando
            // qué corregir, que es justo lo que un mensaje de validación debe evitar.
            .message(ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Solicitud inválida")
            .metadata(Map.of("error", error))
            .build();

        log.warn("IllegalArgumentException en {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );

        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .details("Uno o más campos de la solicitud no son válidos")
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("error", error);
        metadata.put("fieldErrors", fieldErrors);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("Validación de entrada fallida")
            .metadata(metadata)
            .build();

        log.warn("Validación fallida en {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .details("Error interno no esperado: " + ex.getClass().getSimpleName())
            .path(request.getRequestURI())
            .timestamp(System.currentTimeMillis() / 1000)
            .build();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(false)
            .timestamp(LocalDateTime.now())
            .message("Error interno del servidor")
            .metadata(Map.of("error", error))
            .build();

        log.error("Excepción no manejada en {}: ", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
