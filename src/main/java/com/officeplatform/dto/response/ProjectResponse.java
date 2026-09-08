package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lightweight project (API key) reference used by the share modal's project selector. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private Long id;

    private String projectName;

    /**
     * True cuando el proyecto ya usa la plataforma: tiene archivos, sesiones de edición o
     * usuarios registrados. Permite distinguir de un vistazo los aplicativos reales de las
     * API keys creadas para una prueba y nunca usadas.
     */
    private boolean activeConsumer;

}
