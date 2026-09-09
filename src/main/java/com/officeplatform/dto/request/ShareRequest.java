package com.officeplatform.dto.request;

import java.time.LocalDateTime;

import com.officeplatform.entity.SharePermissionEntity.PermissionLevel;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareRequest {

    @NotNull(message = "resourceType es obligatorio (FILE | FOLDER)")
    private ResourceType resourceType;

    @NotNull(message = "resourceId es obligatorio")
    private Long resourceId;

    @NotNull(message = "targetType es obligatorio (USER | PROJECT)")
    private TargetType targetType;

    /** Requerido cuando targetType = USER (cédula del destinatario). */
    private String targetUserId;

    /** Requerido cuando targetType = PROJECT (API key del proyecto destino). */
    private Long targetApiKeyId;

    @NotNull(message = "permissionLevel es obligatorio (VIEW | DOWNLOAD | EDIT)")
    private PermissionLevel permissionLevel;

    /** Opcional: observaciones del que comparte. */
    /** Si el destinatario podra volver a compartir el recurso. Ausente = no. */
    private Boolean canShare;

    private String notes;

    /** Opcional: fecha de expiración del permiso. Null = sin vencimiento. */
    private LocalDateTime expiresAt;

}
