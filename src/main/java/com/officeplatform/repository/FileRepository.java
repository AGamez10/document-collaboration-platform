package com.officeplatform.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.FileEntity;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    List<FileEntity> findAllByApiKeyIdAndDeletedAtIsNull(Long apiKeyId);

    List<FileEntity> findAllByApiKeyIdAndDeletedAtIsNotNull(Long apiKeyId);

    List<FileEntity> findAllByApiKeyIdAndFolderIdIsNullAndDeletedAtIsNull(Long apiKeyId);

    List<FileEntity> findAllByApiKeyIdAndFolderIdAndDeletedAtIsNull(Long apiKeyId, Long folderId);

    List<FileEntity> findAllByApiKeyIdAndFolderIdAndUserIdIsNullAndDeletedAtIsNull(Long apiKeyId, Long folderId);

    List<FileEntity> findAllByApiKeyIdAndFolderIdAndUserIdAndDeletedAtIsNull(Long apiKeyId, Long folderId, String userId);

    // Nuevos métodos para filtrado por userId (Cédula) / Compartido (user_id IS NULL)
    List<FileEntity> findAllByApiKeyIdAndFolderIdIsNullAndUserIdIsNullAndDeletedAtIsNull(Long apiKeyId);

    List<FileEntity> findAllByApiKeyIdAndFolderIdIsNullAndUserIdAndDeletedAtIsNull(Long apiKeyId, String userId);

    List<FileEntity> findAllByApiKeyIdAndUserIdIsNullAndDeletedAtIsNotNull(Long apiKeyId);

    List<FileEntity> findAllByApiKeyIdAndUserIdAndDeletedAtIsNotNull(Long apiKeyId, String userId);

    List<FileEntity> findAllByApiKeyIdAndUserIdIsNullAndDeletedAtIsNull(Long apiKeyId);

    List<FileEntity> findAllByApiKeyIdAndUserIdAndDeletedAtIsNull(Long apiKeyId, String userId);

    // Search by file name (LIKE %term%, case-insensitive), active files only, across all folders.
    List<FileEntity> findAllByApiKeyIdAndOriginalFileNameContainingIgnoreCaseAndDeletedAtIsNull(
            Long apiKeyId, String term);

    List<FileEntity> findAllByApiKeyIdAndUserIdAndOriginalFileNameContainingIgnoreCaseAndDeletedAtIsNull(
            Long apiKeyId, String userId, String term);

    List<FileEntity> findAllByApiKeyIdAndUserIdIsNullAndOriginalFileNameContainingIgnoreCaseAndDeletedAtIsNull(
            Long apiKeyId, String term);

    Optional<FileEntity> findByIdAndApiKeyIdAndDeletedAtIsNull(Long id, Long apiKeyId);

    Optional<FileEntity> findByIdAndApiKeyIdAndDeletedAtIsNotNull(Long id, Long apiKeyId);

    /**
     * Lookup that also matches trashed files. Permission resolution must work on trashed
     * resources: restore and purge operate precisely on files whose deletedAt is not null,
     * so a lookup restricted to DeletedAtIsNull can never authorize them.
     */
    Optional<FileEntity> findByIdAndApiKeyId(Long id, Long apiKeyId);

    // ── "Mis archivos" decentralized ────────────────────────────────────────────
    // Private files follow the person, not the consumer application: the same cédula sees the
    // same files from every project, so these queries deliberately omit apiKeyId.
    // "Compartidos" keeps its per-project isolation and does NOT use them.

    List<FileEntity> findAllByUserIdAndFolderIdIsNullAndDeletedAtIsNull(String userId);

    List<FileEntity> findAllByUserIdAndFolderIdAndDeletedAtIsNull(String userId, Long folderId);

    List<FileEntity> findAllByUserIdAndDeletedAtIsNull(String userId);

    List<FileEntity> findAllByUserIdAndDeletedAtIsNotNull(String userId);

    List<FileEntity> findAllByCreatedByUserIdAndDeletedAtIsNotNull(String createdByUserId);

    List<FileEntity> findAllByCreatedByUserIdIsNullAndCreatedByNameAndDeletedAtIsNotNull(String createdByName);

    /**
     * Lookup by id that is not scoped to a project.
     *
     * <p>Needed because a decentralized "Mis archivos" listing can return a file created from a
     * different consumer application; opening or downloading it must resolve. Callers are
     * responsible for checking that the caller owns the file — see {@code FileServiceImpl}.
     */
    Optional<FileEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Trash listing driven by ownership rather than by scope. A shared file is stored with
     * user_id = null, so filtering the trash by user_id hides it from its own author while
     * exposing it to every other member of the project.
     */
    List<FileEntity> findAllByApiKeyIdAndCreatedByUserIdAndDeletedAtIsNotNull(
            Long apiKeyId, String createdByUserId);

    /**
     * Fallback for rows created before created_by_user_id existed, which only carry the
     * creator's display name.
     */
    List<FileEntity> findAllByApiKeyIdAndCreatedByUserIdIsNullAndCreatedByNameAndDeletedAtIsNotNull(
            Long apiKeyId, String createdByName);

    /**
     * Archivos de una carpeta sin filtrar por proyecto. La autorización ocurre sobre la carpeta
     * contenedora; restringir aquí vaciaba el ZIP de toda carpeta compartida entre proyectos.
     */
    List<FileEntity> findAllByFolderIdAndDeletedAtIsNull(Long folderId);

    Optional<FileEntity> findByUuid(String uuid);

    /** Proyectos que tienen al menos un archivo. Usado para marcar consumidores activos. */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT f.apiKeyId FROM FileEntity f")
    java.util.List<Long> findDistinctApiKeyIds();

    // Admin-only queries: not scoped by apiKeyId, used by the cross-tenant admin panel.
    List<FileEntity> findAllByDeletedAtIsNull();

    List<FileEntity> findAllByDeletedAtIsNotNull();

    Optional<FileEntity> findByIdAndDeletedAtIsNotNull(Long id);

    long countByDeletedAtIsNull();

    long countByCreatedAtAfter(LocalDateTime after);

    @Query("select coalesce(sum(f.size), 0) from FileEntity f where f.deletedAt is null")
    long sumSizeOfActiveFiles();

    /**
     * Bytes que ocupa un proyecto, contando solo lo vivo.
     *
     * <p>La papelera no suma: un archivo eliminado ya no le sirve a nadie y cobrarlo contra la
     * cuota dejaria a la gente sin poder subir hasta que alguien vacie la papelera, que es un
     * castigo raro por haber borrado algo. coalesce cubre el proyecto sin archivos, donde sum()
     * devuelve null en vez de cero.
     */
    @Query("select coalesce(sum(f.size), 0) from FileEntity f "
         + "where f.apiKeyId = :apiKeyId and f.deletedAt is null")
    long sumSizeByApiKeyIdAndDeletedAtIsNull(@Param("apiKeyId") Long apiKeyId);

    /** Per-API-key file count + storage usage, active files only. Row shape: [apiKeyId, fileCount, totalSize]. */
    @Query("select f.apiKeyId, count(f), coalesce(sum(f.size), 0) "
            + "from FileEntity f where f.deletedAt is null group by f.apiKeyId")
    List<Object[]> countAndSizeByApiKeyGrouped();

    /** Cuantos archivos vivos tiene un proyecto. */
    long countByApiKeyIdAndDeletedAtIsNull(Long apiKeyId);


    /** Archivos de una carpeta que estan en la papelera, para restaurarlos con ella. */
    List<FileEntity> findAllByFolderIdAndDeletedAtIsNotNull(Long folderId);

    /** Todos los archivos de una carpeta, vivos o en papelera, para purgar el arbol. */
    List<FileEntity> findAllByFolderId(Long folderId);


    // ── Busqueda por nombre o por contenido ─────────────────────────────────
    // Se consultan las dos columnas en una sola pasada. Hacerlo en dos consultas y unir en
    // memoria obligaria a deduplicar los archivos que coinciden por ambas.

    @Query("select f from FileEntity f where f.apiKeyId = :apiKeyId and f.deletedAt is null "
         + "and (lower(f.originalFileName) like lower(concat('%', :term, '%')) "
         + "  or lower(f.searchContent) like lower(concat('%', :term, '%')))")
    List<FileEntity> searchByNameOrContent(@Param("apiKeyId") Long apiKeyId, @Param("term") String term);

    @Query("select f from FileEntity f where f.apiKeyId = :apiKeyId and f.userId = :userId "
         + "and f.deletedAt is null "
         + "and (lower(f.originalFileName) like lower(concat('%', :term, '%')) "
         + "  or lower(f.searchContent) like lower(concat('%', :term, '%')))")
    List<FileEntity> searchByNameOrContentForUser(@Param("apiKeyId") Long apiKeyId,
                                                  @Param("userId") String userId,
                                                  @Param("term") String term);

    @Query("select f from FileEntity f where f.apiKeyId = :apiKeyId and f.userId is null "
         + "and f.deletedAt is null "
         + "and (lower(f.originalFileName) like lower(concat('%', :term, '%')) "
         + "  or lower(f.searchContent) like lower(concat('%', :term, '%')))")
    List<FileEntity> searchSharedByNameOrContent(@Param("apiKeyId") Long apiKeyId,
                                                 @Param("term") String term);


    /**
     * Ids de los archivos vivos que todavia no tienen texto extraido.
     *
     * <p>Devuelve solo ids y no entidades: el reindexado puede abarcar miles de filas, y traerlas
     * enteras para quedarse con su identificador cargaria en memoria el catalogo completo. Cada
     * archivo se vuelve a leer despues, uno por uno, cuando le toca.
     */
    @Query("select f.id from FileEntity f where f.searchContent is null and f.deletedAt is null")
    List<Long> findIdsPendingIndexing();

}
