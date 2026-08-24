package com.officeplatform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

}
