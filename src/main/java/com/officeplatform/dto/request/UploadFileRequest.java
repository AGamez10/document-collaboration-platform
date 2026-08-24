package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileRequest {

    @NotBlank(message = "El nombre del archivo no puede estar vacío")
    @Size(max = 500, message = "El nombre del archivo no puede exceder 500 caracteres")
    private String originalFileName;

    @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
    private String description;

}
