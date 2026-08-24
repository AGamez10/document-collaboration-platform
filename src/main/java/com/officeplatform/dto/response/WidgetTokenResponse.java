package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WidgetTokenResponse {

    /** JWT de corta duración que el widget usará como Bearer token. */
    private String widgetToken;

    /** Segundos hasta que expira el token (28800 = 8 horas). */
    private int expiresIn;

}
