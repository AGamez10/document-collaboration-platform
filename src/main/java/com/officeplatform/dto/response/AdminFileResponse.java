package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminFileResponse {

    private Long id;

    private String uuid;

    private String originalFileName;

    private String mimeType;

    private Long size;

    private Long apiKeyId;

    private String apiKeyName;

    private Long folderId;

    private String folderPath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    /**
     * Momento en que el dueño vacio el archivo de su propia papelera.
     *
     * <p>Con valor, el archivo ya no existe para el usuario y solo se lo ve desde aca: es el
     * estado en el que mas importa que el panel muestre por que nadie mas lo encuentra.
     */
    private LocalDateTime userPurgedAt;

}
