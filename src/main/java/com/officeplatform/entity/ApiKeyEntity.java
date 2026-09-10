package com.officeplatform.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_key", columnList = "api_key", unique = true),
        @Index(name = "idx_api_key_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active;

    /**
     * Tope de almacenamiento del proyecto, en bytes. Null significa sin limite.
     *
     * <p>Anulable a proposito: los proyectos que ya existen trabajan sin tope, y un valor por
     * defecto les habria impuesto uno retroactivo cuyo primer sintoma seria gente sin poder subir.
     */
    @Column(name = "storage_quota_bytes")
    private Long storageQuotaBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.active == null) {
            this.active = true;
        }
        this.createdAt = LocalDateTime.now();
    }

}
