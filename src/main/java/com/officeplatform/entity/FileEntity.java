package com.officeplatform.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_file_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_file_api_key_id", columnList = "api_key_id"),
        @Index(name = "idx_file_created_at", columnList = "created_at"),
        @Index(name = "idx_file_folder_id", columnList = "folder_id"),
        @Index(name = "idx_file_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "bucket", nullable = false)
    private String bucket;

    @Column(name = "object_name", nullable = false)
    private String objectName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "extension")
    private String extension;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "created_by_name")
    private String createdByName;

    /** Creator's user id (cédula from the JWT), always set on create regardless of private/shared scope. */
    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @Column(name = "updated_by_name")
    private String updatedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
