package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameFolderRequest {

    @NotBlank(message = "El nombre de la carpeta no puede estar vacío")
    @Size(max = 255, message = "El nombre de la carpeta no puede exceder 255 caracteres")
    private String name;

}
