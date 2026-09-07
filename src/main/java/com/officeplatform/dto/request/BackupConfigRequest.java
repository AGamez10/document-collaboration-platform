package com.officeplatform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupConfigRequest {

    @NotNull(message = "El estado habilitado/deshabilitado es obligatorio")
    private Boolean enabled;

    @NotNull(message = "El intervalo en horas es obligatorio")
    @Min(value = 1, message = "El intervalo mínimo es de 1 hora")
    @Max(value = 720, message = "El intervalo máximo es de 720 horas (30 días)")
    private Integer intervalHours;

    @NotNull(message = "La cantidad máxima de backups a retener es obligatoria")
    @Min(value = 1, message = "Debe retener al menos 1 backup")
    @Max(value = 100, message = "No se pueden retener más de 100 backups")
    private Integer maxRetainedBackups;

    /** Primary directory where packages are written. Blank keeps the current one. */
    private String backupDirectory;

    /** Secondary location (NAS / DataServer) each package is copied to. Blank disables the copy. */
    private String replicationDirectory;

    /** Whether every new package is replicated to {@code replicationDirectory}. */
    private Boolean replicationEnabled;

}
