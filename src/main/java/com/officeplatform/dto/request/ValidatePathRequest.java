package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidatePathRequest {

    @NotBlank(message = "La ruta es obligatoria")
    private String path;
}
