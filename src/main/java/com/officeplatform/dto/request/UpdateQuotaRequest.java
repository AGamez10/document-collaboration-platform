package com.officeplatform.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nueva cuota de un proyecto, expresada en gigabytes. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuotaRequest {

    /**
     * Gigabytes asignados. Null quita el límite.
     *
     * <p>Se recibe en GB porque es la unidad en la que un administrador piensa, y se guarda en
     * bytes: almacenar la unidad derivada obligaría a redondear en cada comparación de cuota.
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "La cuota debe ser mayor que cero, o null para quitar el límite")
    private Double quotaGb;
}
