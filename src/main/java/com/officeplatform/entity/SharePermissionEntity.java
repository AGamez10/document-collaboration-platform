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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Granular sharing grant for a file or folder. Permissions are an OPTIONAL, additive layer over the
 * existing "Compartidos" model: a shared resource (userId = null) with NO rows here stays visible to
 * everyone in the project, exactly as today. Rows here only ever RESTRICT/EXTEND visibility, never
 * replace the existing listing queries.
 *
 * <p><b>Business rule — who may manage a resource's permissions (enforced in Sub-Fase 4.2, in the
 * share endpoint validations):</b>
 * <ul>
 *   <li>The <b>creator</b> of a resource has full control over its permissions regardless of role:
 *       when the JWT {@code userId} matches the resource creator's userId, they may share, restrict
 *       and revoke. This is creator autonomy.</li>
 *   <li>A <b>project admin</b> ({@link KnownUserEntity#getRole()} == "admin") may manage the
 *       permissions of ANY resource in their project, not only the ones they created.</li>
 * </ul>
 *
 * <p>apiKeyId fields are {@code Long}, consistent with apiKeyId across the codebase, so they can be
 * cross-referenced against {@code FileEntity}/{@code FolderEntity}/{@code KnownUserEntity}.
 */
@Entity
@Table(name = "share_permissions", indexes = {
        @Index(name = "idx_share_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_share_source_api_key", columnList = "source_api_key_id"),
        @Index(name = "idx_share_target_user", columnList = "target_user_id"),
        @Index(name = "idx_share_target_api_key", columnList = "target_api_key_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── What is shared ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    // ── From which project ──────────────────────────────────────────────────
    @Column(name = "source_api_key_id", nullable = false)
    private Long sourceApiKeyId;

    // ── With whom ───────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    /** Set when targetType = USER: the recipient's cédula. */
    @Column(name = "target_user_id")
    private String targetUserId;

    /** Set when targetType = PROJECT: the recipient project's API key id. */
    @Column(name = "target_api_key_id")
    private Long targetApiKeyId;

    // ── Access level ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false)
    private PermissionLevel permissionLevel;

    /**
     * Whether the recipient may re-share the resource with other people.
     *
     * <p><b>A flag and not a fourth {@link PermissionLevel} on purpose.</b> Hibernate emits a CHECK
     * constraint listing the enum values when it creates the table, and {@code ddl-auto=update}
     * never alters it: {@code share_permissions_permission_level_check} still allows only VIEW,
     * DOWNLOAD and EDIT on every database created before this change. A new enum constant would
     * compile, pass the tests against H2 — which builds the schema from scratch — and then fail in
     * production the first time someone granted it. The same trap already broke this project twice
     * through {@code activity_log_action_check}.
     *
     * <p>{@code Boolean} rather than {@code boolean}, and nullable in the database, because
     * {@code ddl-auto=update} adds the column to a table that already holds rows: a NOT NULL column
     * with no default would fail outright. Existing grants read back as null, which
     * {@link #canReshare()} treats as "not allowed" — the safe reading for a permission.
     */
    @Column(name = "can_share")
    private Boolean canShare;

    /** Null-safe read: an older grant with no value stored may not re-share. */
    public boolean canReshare() {
        return Boolean.TRUE.equals(canShare);
    }

    // ── Who shared it ───────────────────────────────────────────────────────
    @Column(name = "shared_by_user_id")
    private String sharedByUserId;

    @Column(name = "shared_by_name")
    private String sharedByName;

    /** Optional free-text note the sharer leaves when granting access. */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── When ────────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Null = no expiration. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum ResourceType { FILE, FOLDER }

    public enum TargetType { USER, PROJECT }

    public enum PermissionLevel { VIEW, DOWNLOAD, EDIT }

}
