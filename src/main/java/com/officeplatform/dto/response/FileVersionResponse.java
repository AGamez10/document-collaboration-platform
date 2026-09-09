package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Una entrada del historial de un archivo, tal como la ve quien lo consulta. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVersionResponse {

    private Long id;

    /** Correlativo por archivo. Es lo que la persona identifica, no el id. */
    private Integer versionNumber;

    private Long size;

    private String createdByUserId;

    private String createdByName;

    private LocalDateTime createdAt;

    /** Por qué existe esta versión: un guardado del editor o una restauración. */
    private String comment;
}
