package com.officeplatform.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activity_log", indexes = {
        @Index(name = "idx_activity_log_api_key_id", columnList = "api_key_id"),
        @Index(name = "idx_activity_log_timestamp", columnList = "timestamp"),
        @Index(name = "idx_activity_log_action", columnList = "action")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    /**
     * Hibernate emits a CHECK constraint listing the enum values when it creates this table, and
     * {@code ddl-auto=update} never alters it afterwards. A value added to {@link ActivityAction}
     * later is therefore rejected by any database created before it existed — adding SHARE and
     * UNSHARE is what made {@code POST /api/share} fail with a 500 on the running instance.
     *
     * <p>Writes go through {@code ActivityLogWriter} in their own transaction so a rejected entry
     * can no longer roll back the business operation that triggered it. Reconciling the constraint
     * on an existing database still has to be done explicitly; see README, "Deuda técnica conocida".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ActivityAction action;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

}
