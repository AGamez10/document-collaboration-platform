package com.officeplatform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.FolderEntity;

@Repository
public interface FolderRepository extends JpaRepository<FolderEntity, Long> {

    List<FolderEntity> findAllByApiKeyIdAndParentIdIsNull(Long apiKeyId);

    List<FolderEntity> findAllByApiKeyIdAndParentIdIsNullAndUserIdIsNull(Long apiKeyId);

    List<FolderEntity> findAllByApiKeyIdAndParentIdIsNullAndUserId(Long apiKeyId, String userId);

    List<FolderEntity> findAllByApiKeyIdAndParentId(Long apiKeyId, Long parentId);

    /**
     * Subcarpetas sin filtrar por proyecto. Una carpeta compartida entre proyectos contiene
     * subcarpetas y archivos de varios api_key_id; filtrar por el del llamador devuelve cero.
     */
    List<FolderEntity> findAllByParentId(Long parentId);

    List<FolderEntity> findAllByApiKeyIdAndParentIdAndUserIdIsNull(Long apiKeyId, Long parentId);

    List<FolderEntity> findAllByApiKeyIdAndParentIdAndUserId(Long apiKeyId, Long parentId, String userId);

    // Search by folder name (LIKE %term%, case-insensitive), across all parents.
    List<FolderEntity> findAllByApiKeyIdAndNameContainingIgnoreCase(Long apiKeyId, String term);

    List<FolderEntity> findAllByApiKeyIdAndUserIdAndNameContainingIgnoreCase(Long apiKeyId, String userId, String term);

    List<FolderEntity> findAllByApiKeyIdAndUserIdIsNullAndNameContainingIgnoreCase(Long apiKeyId, String term);

    // ── "Mis archivos" decentralized ────────────────────────────────────────────
    // Private folders follow the person, not the consumer application, so these queries omit
    // apiKeyId on purpose. "Compartidos" keeps its per-project isolation and does NOT use them.

    List<FolderEntity> findAllByUserIdAndParentIdIsNull(String userId);

    List<FolderEntity> findAllByUserIdAndParentId(String userId, Long parentId);

    Optional<FolderEntity> findByIdAndApiKeyId(Long id, Long apiKeyId);

    Optional<FolderEntity> findByUuid(String uuid);

    // ── Papelera de carpetas ────────────────────────────────────────────────
    // Las consultas de arriba NO filtran por deletedAt a propósito: las usa el respaldo, que debe
    // llevarse la papelera con sus marcas de tiempo, y la restauración, que necesita encontrar
    // justamente lo eliminado. Los listados del gestor usan las variantes de acá abajo.

    List<FolderEntity> findAllByApiKeyIdAndParentIdIsNullAndDeletedAtIsNull(Long apiKeyId);

    List<FolderEntity> findAllByApiKeyIdAndParentIdIsNullAndUserIdIsNullAndDeletedAtIsNull(Long apiKeyId);

    List<FolderEntity> findAllByApiKeyIdAndParentIdAndDeletedAtIsNull(Long apiKeyId, Long parentId);

    List<FolderEntity> findAllByParentIdAndDeletedAtIsNull(Long parentId);

    List<FolderEntity> findAllByUserIdAndParentIdIsNullAndDeletedAtIsNull(String userId);

    List<FolderEntity> findAllByUserIdAndParentIdAndDeletedAtIsNull(String userId, Long parentId);

    List<FolderEntity> findAllByApiKeyIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(Long apiKeyId, String term);

    List<FolderEntity> findAllByApiKeyIdAndUserIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
            Long apiKeyId, String userId, String term);

    List<FolderEntity> findAllByApiKeyIdAndUserIdIsNullAndNameContainingIgnoreCaseAndDeletedAtIsNull(
            Long apiKeyId, String term);

    /** Carpetas que una persona envió a su papelera, sin filtrar por proyecto. */
    List<FolderEntity> findAllByDeletedByUserIdAndDeletedAtIsNotNull(String deletedByUserId);

    /** Subcarpetas eliminadas, para poder restaurar o purgar el árbol completo. */
    List<FolderEntity> findAllByParentIdAndDeletedAtIsNotNull(Long parentId);

}
