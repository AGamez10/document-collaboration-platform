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
 * Credentials for someone signing in to the web portal directly, without a consuming application.
 *
 * <p>Separate from {@link KnownUserEntity} on purpose: that one is a directory of people seen
 * operating through a project's API key and holds no secret. This one holds a password hash, so it
 * gets its own table and its own lifecycle.
 *
 * <p>The cédula is unique across the whole platform, not per project: a person has one portal
 * account regardless of how many applications they also use.
 */
@Entity
@Table(
        name = "portal_users",
        indexes = { @Index(name = "idx_portal_user_cedula", columnList = "cedula", unique = true) },
        uniqueConstraints = { @UniqueConstraint(name = "uk_portal_user_cedula", columnNames = { "cedula" }) })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cedula", nullable = false, unique = true)
    private String cedula;

    @Column(name = "display_name")
    private String displayName;

    /** BCrypt hash. The plain password is never stored, logged or returned. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * True while the account still carries the provisional password handed out on first sign-in.
     * The portal refuses to issue a working session until this is cleared.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

}
