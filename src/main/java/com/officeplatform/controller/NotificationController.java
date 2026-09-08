package com.officeplatform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.officeplatform.dto.response.ApiResponse;
import com.officeplatform.dto.response.NotificationListResponse;
import com.officeplatform.security.model.ApiKeyPrincipal;
import com.officeplatform.service.notification.NotificationService;

/**
 * Notifications belonging to the calling person.
 *
 * <p>The recipient is always taken from the caller's identity, never from a parameter: accepting
 * one would let anybody read somebody else's notifications by changing a number in the URL.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        NotificationListResponse data = notificationService.list(principal.resolveUserId(userId));

        ApiResponse<NotificationListResponse> response = ApiResponse.<NotificationListResponse>builder()
                .success(true)
                .message("Notificaciones obtenidas correctamente")
                .data(data)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Boolean>> markAsRead(
            @PathVariable Long id,
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        boolean marked = notificationService.markAsRead(id, principal.resolveUserId(userId));

        ApiResponse<Boolean> response = ApiResponse.<Boolean>builder()
                .success(true)
                .message(marked ? "Notificación marcada como leída" : "La notificación no existe o no es tuya")
                .data(marked)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal ApiKeyPrincipal principal) {

        int marked = notificationService.markAllAsRead(principal.resolveUserId(userId));

        ApiResponse<Integer> response = ApiResponse.<Integer>builder()
                .success(true)
                .message(marked + " notificación(es) marcada(s) como leída(s)")
                .data(marked)
                .build();

        return ResponseEntity.ok(response);
    }

}
