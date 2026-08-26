package com.officeplatform.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Directory of end users the platform has "seen" per consumer project (API key). There is no
 * external user table and the Widget Token JWT carries no role, so this table is the single home
 * for both the known-user directory and the consumer-level {@code role}. It is populated
 * automatically: every authenticated operation upserts the acting user via
 * {@link com.officeplatform.service.user.KnownUserService#registerActivity} (hooked into
 * {@code ActivityLogRecorder}). Uniqueness is per (apiKeyId, userId).
 */
@Entity
@Table(
        name = "known_users",
        indexes = {
                @Index(name = "idx_known_user_api_key_id", columnList = "api_key_id"),
                @Index(name = "idx_known_user_api_key_user", columnList = "api_key_id, user_id", unique = true)
        },
        // Declared as a named table constraint as well as a unique index so the rule is explicit in
        // the schema and violations surface with a stable constraint name the service can react to.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_known_user_api_key_user", columnNames = { "api_key_id", "user_id" })
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnownUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Consumer project this user belongs to. Long, consistent with apiKeyId across the codebase. */
    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    /** Unique user identifier (cédula) as carried by the Widget Token. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "display_name")
    private String displayName;

    /** "admin" | "user". Defaults to "user"; only a platform admin promotes to "admin". */
    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

}
