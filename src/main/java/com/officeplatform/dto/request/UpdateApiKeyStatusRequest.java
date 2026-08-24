package com.officeplatform.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApiKeyStatusRequest {

    @NotNull(message = "El campo active es obligatorio")
    private Boolean active;

}
