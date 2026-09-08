package com.officeplatform.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.EditorSessionEntity;

@Repository
public interface EditorSessionRepository extends JpaRepository<EditorSessionEntity, Long> {

    long countByClosedAtIsNull();

    long countByClosedAtIsNullAndApiKeyId(Long apiKeyId);

    List<EditorSessionEntity> findAllByClosedAtIsNull();

    List<EditorSessionEntity> findAllByClosedAtIsNullAndApiKeyId(Long apiKeyId);

    List<EditorSessionEntity> findAllByFileIdAndClosedAtIsNull(Long fileId);

    List<EditorSessionEntity> findAllByDocumentKeyAndClosedAtIsNull(String documentKey);

    Optional<EditorSessionEntity> findByIdAndClosedAtIsNull(Long id);

    /**
     * Open sessions whose last sign of life is older than the cutoff.
     *
     * <p>COALESCE on purpose: rows created before last_heartbeat_at existed have it null, and a
     * null would never compare as stale — those sessions would hold an OnlyOffice connection
     * forever. Falling back to opened_at makes them expire like any other.
     */
    @Query("SELECT s FROM EditorSessionEntity s WHERE s.closedAt IS NULL "
            + "AND COALESCE(s.lastHeartbeatAt, s.openedAt) < :cutoff")
    List<EditorSessionEntity> findStaleSessions(@Param("cutoff") LocalDateTime cutoff);

}
