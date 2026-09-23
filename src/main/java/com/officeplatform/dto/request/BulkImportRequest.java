package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** Migración de una carpeta del servidor —el DataServer montado como volumen— hacia el gestor. */
@Data
public class BulkImportRequest {

    @NotBlank(message = "Indicá la carpeta del servidor que querés importar")
    private String sourceDirectoryPath;

    @NotNull(message = "Indicá a qué proyecto entran los archivos")
    private Long targetApiKeyId;

    /** Carpeta destino dentro del proyecto, o null para la raíz del espacio. */
    private Long targetFolderId;

    /** Cédula que queda como autora de lo importado. */
    private String defaultUserId;

    /** 'shared' para el espacio del proyecto, 'private' para Mis Archivos de esa cédula. */
    private String scope;

}
