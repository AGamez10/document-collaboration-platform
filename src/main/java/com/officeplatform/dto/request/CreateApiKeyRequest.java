package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequest {

    @NotBlank(message = "El nombre de la API key no puede estar vacío")
    @Size(max = 255, message = "El nombre de la API key no puede exceder 255 caracteres")
    private String name;

}
