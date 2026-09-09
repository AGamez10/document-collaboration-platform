package com.officeplatform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.officeplatform.entity.FileVersionEntity;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersionEntity, Long> {

    /** Historial de un archivo, de la versión más reciente a la más antigua. */
    List<FileVersionEntity> findAllByFileIdOrderByVersionNumberDesc(Long fileId);

    Optional<FileVersionEntity> findByFileIdAndVersionNumber(Long fileId, Integer versionNumber);

    /** Última versión archivada, para saber qué número le toca a la siguiente. */
    Optional<FileVersionEntity> findFirstByFileIdOrderByVersionNumberDesc(Long fileId);

    List<FileVersionEntity> findAllByFileId(Long fileId);

}
