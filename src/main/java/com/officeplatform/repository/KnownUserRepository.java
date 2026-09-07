package com.officeplatform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.KnownUserEntity;

@Repository
public interface KnownUserRepository extends JpaRepository<KnownUserEntity, Long> {

    Optional<KnownUserEntity> findByApiKeyIdAndUserId(Long apiKeyId, String userId);

    List<KnownUserEntity> findAllByApiKeyId(Long apiKeyId);

    // Autocomplete for the share modal: match by display name (LIKE %term%, case-insensitive).
    List<KnownUserEntity> findAllByApiKeyIdAndDisplayNameContainingIgnoreCase(Long apiKeyId, String term);

    List<KnownUserEntity> findAllByOrderByLastSeenAtDesc();

    @org.springframework.data.jpa.repository.Query("SELECT u FROM KnownUserEntity u WHERE LOWER(u.displayName) LIKE LOWER(CONCAT('%', :term, '%')) OR u.userId LIKE CONCAT('%', :term, '%') ORDER BY u.lastSeenAt DESC")
    List<KnownUserEntity> searchAllByTerm(@org.springframework.data.repository.query.Param("term") String term);

}
