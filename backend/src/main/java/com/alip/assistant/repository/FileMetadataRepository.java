package com.alip.assistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for file metadata.
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadataEntity, Long> {

    Optional<FileMetadataEntity> findByAbsolutePath(String path);

    List<FileMetadataEntity> findByFileNameContainingIgnoreCase(String query);

    long countByIndexedTrue();

    List<FileMetadataEntity> findByIntegrationName(String name);
}
