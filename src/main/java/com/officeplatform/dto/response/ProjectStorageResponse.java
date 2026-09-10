package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Consumo de almacenamiento de un proyecto y su tope, para el panel de administración. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectStorageResponse {

    private Long apiKeyId;

    private String projectName;

    private long usedBytes;

    private String formattedUsed;

    /** Null significa sin límite: es como funcionan los proyectos que nadie configuró. */
    private Long quotaBytes;

    private String formattedQuota;

    /** Porcentaje de uso, null cuando no hay cuota contra la cual medirlo. */
    private Double usagePercent;

    /**
     * NORMAL, WARNING (>85%), CRITICAL (>95%) o UNLIMITED.
     *
     * <p>Se calcula en el servidor y no en el panel para que la misma regla valga en cualquier
     * cliente que consulte esto: un umbral duplicado en el frontend se desincroniza en la primera
     * vez que alguien decide moverlo.
     */
    private String status;

    private long fileCount;
}
