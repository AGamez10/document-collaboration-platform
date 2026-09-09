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
@Table(name = "folders", indexes = {
        @Index(name = "idx_folder_uuid", columnList = "uuid", unique = true),
        @Index(name = "idx_folder_api_key_id", columnList = "api_key_id"),
        @Index(name = "idx_folder_parent_id", columnList = "parent_id"),
        @Index(name = "idx_folder_user_id", columnList = "user_id"),
        @Index(name = "idx_folder_deleted_at", columnList = "deleted_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

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

    /**
     * Papelera de carpetas. Null significa activa.
     *
     * <p>Antes las carpetas se borraban físicamente y sus archivos perdían el {@code folderId} para
     * no quedar apuntando a una fila inexistente. El costo era que restaurar un archivo lo dejaba en
     * la raíz: su ubicación original ya no existía en ninguna parte. Con el borrado lógico la
     * jerarquía sobrevive y la restauración puede devolver cada cosa a su lugar exacto.
     *
     * <p>Columna anulable a propósito: {@code ddl-auto=update} la agrega sobre una tabla con filas,
     * y las carpetas existentes quedan con null, que es justo lo que significa "no eliminada".
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Quién la envió a la papelera, para que cada persona vea la suya. */
    @Column(name = "deleted_by_user_id")
    private String deletedByUserId;

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
