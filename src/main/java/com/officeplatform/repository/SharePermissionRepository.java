package com.officeplatform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.SharePermissionEntity;
import com.officeplatform.entity.SharePermissionEntity.ResourceType;
import com.officeplatform.entity.SharePermissionEntity.TargetType;

@Repository
public interface SharePermissionRepository extends JpaRepository<SharePermissionEntity, Long> {

    /** Permissions attached to a specific resource within its source project. */
    List<SharePermissionEntity> findAllByResourceTypeAndResourceIdAndSourceApiKeyId(
            ResourceType resourceType, Long resourceId, Long sourceApiKeyId);

    /** All resources shared directly WITH a specific user (by cédula). */
    List<SharePermissionEntity> findAllByTargetTypeAndTargetUserId(TargetType targetType, String targetUserId);

    /** All permissions attached to a specific resource across projects. */
    List<SharePermissionEntity> findAllByResourceTypeAndResourceId(ResourceType resourceType, Long resourceId);

    /** All resources shared WITH a specific project (by target API key id). */
    List<SharePermissionEntity> findAllByTargetTypeAndTargetApiKeyId(TargetType targetType, Long targetApiKeyId);


    /** Concesiones dirigidas a una persona concreta sobre un recurso concreto. */
    List<SharePermissionEntity> findAllByResourceTypeAndResourceIdAndTargetTypeAndTargetUserId(
            ResourceType resourceType, Long resourceId, TargetType targetType, String targetUserId);


    // ── Papelera por persona ────────────────────────────────────────────────
    // El listado de "Compartidos conmigo" solo muestra los vinculos vivos; la papelera del
    // usuario muestra los descartados. Las consultas de arriba NO filtran a proposito: las usa
    // el respaldo, que debe llevarse la papelera, y la restauracion.

    /** Lo que le compartieron y no descarto: alimenta "Compartidos conmigo". */
    List<SharePermissionEntity> findAllByTargetTypeAndTargetUserIdAndDeletedAtIsNull(
            TargetType targetType, String targetUserId);

    /** Lo que le compartieron y mando a su papelera. */
    List<SharePermissionEntity> findAllByTargetTypeAndTargetUserIdAndDeletedAtIsNotNull(
            TargetType targetType, String targetUserId);

}
