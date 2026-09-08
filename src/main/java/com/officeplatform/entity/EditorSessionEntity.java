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
 * Tracks OnlyOffice editor open/close events so the admin panel can show active
 * connections without relying on OnlyOffice Document Server exposing that data itself
 * (it doesn't, in this deployment — /info/info.json returns 403 and /healthcheck has no
 * connection counters).
 */
@Entity
@Table(name = "editor_sessions", indexes = {
        @Index(name = "idx_editor_session_closed_at", columnList = "closed_at"),
        @Index(name = "idx_editor_session_file_id", columnList = "file_id"),
        @Index(name = "idx_editor_session_api_key_id", columnList = "api_key_id"),
        @Index(name = "idx_editor_session_document_key", columnList = "document_key")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditorSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * The OnlyOffice document key issued for this specific open (EditorServiceImpl
     * mints a fresh key per call), used to match the exact session a callback or an
     * explicit widget close refers to.
     */
    @Column(name = "document_key", nullable = false)
    private String documentKey;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /**
     * Last sign of life from the browser holding this session.
     *
     * <p>Null on rows created before the column existed, so every query that uses it falls back
     * to {@code openedAt}: an old session is judged by when it opened rather than being treated
     * as infinitely fresh.
     */
    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

}
