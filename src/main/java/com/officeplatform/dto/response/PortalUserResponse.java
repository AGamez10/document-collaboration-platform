package com.officeplatform.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un usuario del portal web, visto desde el panel de administración.
 *
 * <p>Sin el hash de la contraseña ni nada derivado de él. El panel necesita saber quién puede
 * entrar y cuándo lo hizo por última vez; para ayudar a alguien que olvidó su clave alcanza con
 * poder restablecerla a la provisional, no con verla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalUserResponse {

    private Long id;

    private String cedula;

    private String displayName;

    /** Si todavía tiene la clave provisional y el portal le va a exigir cambiarla. */
    private boolean mustChangePassword;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

}
