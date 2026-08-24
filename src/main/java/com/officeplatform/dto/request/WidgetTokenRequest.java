package com.officeplatform.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WidgetTokenRequest {

    /**
     * Identificador único del usuario (Cédula, documento, ID de base de datos o username).
     * Acepta 'cedula', 'userId', 'documento', 'idUsuario' o 'user_id'.
     */
    @NotBlank(message = "El identificador de usuario (cédula / userId / documento) es obligatorio")
    @Size(min = 1, max = 100, message = "El identificador debe tener entre 1 y 100 caracteres")
    @JsonAlias({"userId", "documento", "idUsuario", "user_id", "cc", "username"})
    private String cedula;

    /**
     * Nombre completo para mostrar en el editor y la trazabilidad.
     * Acepta 'nombre', 'userName', 'nombreCompleto', 'name' o 'display_name'.
     */
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    @JsonAlias({"userName", "nombreCompleto", "name", "display_name", "user_name"})
    private String nombre;

    /**
     * API key activa del proyecto consumidor.
     * Opcional en el body si se envía por el header X-Api-Key.
     */
    @JsonAlias({"apiKey", "api_key", "key"})
    private String apiKey;

    public String getResolvedUserId() {
        if (cedula != null && !cedula.isBlank()) {
            return cedula.trim();
        }
        return "anon";
    }

    public String getResolvedUserName() {
        if (nombre != null && !nombre.isBlank()) {
            return nombre.trim();
        }
        return getResolvedUserId();
    }

}
