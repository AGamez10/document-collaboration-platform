package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/** Cambio de contraseña del administrador: la actual se pide aunque la sesión ya esté abierta. */
@Data
public class ChangeAdminPasswordRequest {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String currentPassword;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    private String newPassword;

}
