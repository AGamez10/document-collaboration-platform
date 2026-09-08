package com.officeplatform.service.notification;

import com.officeplatform.dto.response.NotificationListResponse;

/**
 * Notifications addressed to the owner of a resource.
 *
 * <p>Recording one must never affect the operation that triggered it: someone opening a document
 * gets their editor regardless of whether the author could be notified.
 */
public interface NotificationService {

    /** Latest notifications for this person, newest first, with the unread count. */
    NotificationListResponse list(String recipientUserId);

    /**
     * Records that somebody opened a resource belonging to another person.
     *
     * <p>Does nothing when the actor is the owner: telling people they opened their own file is
     * noise that would bury the notifications that matter.
     */
    void notifyResourceOpened(String ownerUserId, String actorUserId, String actorName,
                              Long resourceId, String resourceType, String resourceName);

    /**
     * Records any action somebody performed on a resource belonging to another person.
     *
     * <p>Rate-limited per (recipient, actor, resource, title): opening a folder issues a listing
     * on every navigation, and without a cooldown a single browsing session would bury the
     * recipient in dozens of identical messages.
     */
    void notifyResourceAction(String ownerUserId, String actorUserId, String actorName,
                              Long resourceId, String resourceType, String title, String message);

    /** @return true if the notification existed, belonged to this person and was marked read */
    boolean markAsRead(Long notificationId, String recipientUserId);

    /** @return how many were pending */
    int markAllAsRead(String recipientUserId);
}
