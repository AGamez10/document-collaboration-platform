package com.officeplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of a portal sign-in.
 *
 * <p>When {@code mustChangePassword} is true the {@code token} is deliberately null: the account
 * still carries the provisional password, and handing out a usable session before it is changed
 * would make the whole requirement decorative.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalLoginResponse {

    private boolean success;
    private boolean mustChangePassword;
    private String token;
    private Long expiresIn;
    private String cedula;
    private String displayName;
}
