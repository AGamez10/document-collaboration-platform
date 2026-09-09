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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado anterior de un archivo, conservado cada vez que alguien guarda desde el editor.
 *
 * <p>Hasta ahora guardar sobrescribía el objeto en el almacenamiento y el contenido previo se
 * perdía sin rastro: una fórmula borrada por accidente a las cinco de la tarde no tenía vuelta
 * atrás. Cada fila apunta a una copia completa del binario, no a un diff, porque los formatos
 * ofimáticos son ZIP comprimidos y un diff binario sobre ellos no es ni pequeño ni interpretable.
 *
 * <p>La versión guarda el estado <b>previo</b> al guardado, no el nuevo. El contenido vigente vive
 * donde siempre, en {@code files.object_name}: así la descarga y el editor no cambian, y restaurar
 * una versión es copiar su objeto sobre el actual.
 */
@Entity
@Table(name = "file_versions",
        indexes = {
                @Index(name = "idx_file_version_file_id", columnList = "file_id"),
                @Index(name = "idx_file_version_created_at", columnList = "created_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_file_version_number", columnNames = {"file_id", "version_number"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /** Correlativo por archivo, empezando en 1. Es lo que ve la persona, no el id. */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** Objeto en el almacenamiento con el contenido de esta versión. */
    @Column(name = "object_name", nullable = false)
    private String objectName;

    @Column(name = "size")
    private Long size;

    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Por qué existe esta versión: un guardado del editor o una restauración. */
    @Column(name = "comment")
    private String comment;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

}
