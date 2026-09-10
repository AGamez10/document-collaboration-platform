package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {

    private Long id;

    private String uuid;

    private String originalFileName;

    private String mimeType;

    private Long size;

    private Long folderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    private String createdByName;

    private String createdByUserId;

    private String updatedByName;

    /** True when this resource has share permissions attached (restricted). Set by the shared-space filter. */
    /**
     * Si el archivo aparecio en una busqueda por su contenido y no por su nombre.
     *
     * <p>Sin esto, un resultado cuyo nombre no se parece en nada a lo buscado se lee como un
     * error del buscador. Saber que la coincidencia estaba adentro del documento convierte esa
     * sorpresa en la respuesta que la persona buscaba.
     */
    private boolean matchedByContent;

    private boolean restricted;

}
