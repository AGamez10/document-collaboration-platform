package com.officeplatform.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The notification list plus its unread count.
 *
 * <p>Both travel together because the widget needs them at the same moment — the badge and the
 * panel are drawn from one poll, not two.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListResponse {

    private List<NotificationResponse> notifications;
    private long unreadCount;
}
