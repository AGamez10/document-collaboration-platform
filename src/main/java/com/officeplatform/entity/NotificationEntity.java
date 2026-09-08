package com.officeplatform.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A message addressed to the owner of a resource about something that happened to it.
 *
 * <p>Indexed on {@code recipient_user_id} because every read is "the notifications of this
 * person": without it, each poll from each open widget scans the whole table.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_user_id"),
        @Index(name = "idx_notification_recipient_read", columnList = "recipient_user_id, read_flag")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cédula of the person this notification is for — normally the resource's author. */
    @Column(name = "recipient_user_id", nullable = false)
    private String recipientUserId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "resource_type")
    private String resourceType;

    /** Cédula of whoever caused the event. */
    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    /**
     * Mapped to read_flag: "read" is a reserved word in several databases, and letting Hibernate
     * quote it would make hand-written queries depend on the exact quoting style.
     */
    @Column(name = "read_flag", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
