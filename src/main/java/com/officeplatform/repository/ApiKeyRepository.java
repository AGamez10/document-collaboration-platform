package com.officeplatform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.ApiKeyEntity;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    Optional<ApiKeyEntity> findByApiKeyAndActiveTrue(String apiKey);

    /** Lookup regardless of the active flag, used when restoring a backup. */
    Optional<ApiKeyEntity> findByApiKey(String apiKey);

    long countByActiveTrue();

    List<ApiKeyEntity> findAllByActiveTrue();

    List<ApiKeyEntity> findAllByActiveTrueAndNameContainingIgnoreCase(String term);

}
