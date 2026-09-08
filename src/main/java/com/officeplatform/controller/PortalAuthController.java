package com.officeplatform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.officeplatform.dto.request.PortalChangePasswordRequest;
import com.officeplatform.dto.request.PortalLoginRequest;
import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.PortalLoginResponse;
import com.officeplatform.service.portal.PortalAuthService;

/**
 * Sign-in for the web portal, where people reach the file manager without a consuming application.
 *
 * <p>Public like {@code /api/auth/resolve}: it is the front door, so it cannot require the very
 * credential it hands out. Authorisation happens afterwards, through the widget token it issues.
 */
@RestController
@RequestMapping("/api/portal/auth")
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    public PortalAuthController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    /**
     * Signs a person in by cédula.
     *
     * <p>Answers {@code mustChangePassword: true} and no token while the account still carries the
     * provisional password, so the front-end has nothing usable to proceed with until it is changed.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<PortalLoginResponse>> login(
            @Valid @RequestBody PortalLoginRequest request) {

        PortalLoginResponse data = portalAuthService.login(request);

        ApiResponse<PortalLoginResponse> response = ApiResponse.<PortalLoginResponse>builder()
                .success(true)
                .message(data.isMustChangePassword()
                        ? "Debés cambiar la contraseña provisional antes de continuar"
                        : "Sesión iniciada correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    /** Replaces the provisional password and returns the working session. */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<PortalLoginResponse>> changePassword(
            @Valid @RequestBody PortalChangePasswordRequest request) {

        PortalLoginResponse data = portalAuthService.changePassword(request);

        ApiResponse<PortalLoginResponse> response = ApiResponse.<PortalLoginResponse>builder()
                .success(true)
                .message("Contraseña actualizada correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

}
