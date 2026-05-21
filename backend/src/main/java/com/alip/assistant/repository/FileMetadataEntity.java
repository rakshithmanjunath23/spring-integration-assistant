package com.alip.assistant.repository;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing metadata for a discovered and indexed source file.
 */
@Entity
@Table(name = "file_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 1024)
    private String absolutePath;

    @Column(length = 512)
    private String relativePath;

    @Column(nullable = false)
    private String fileName;

    private String fileType; // java, xml, yaml, properties, txt, md

    private long fileSize;

    @Column(length = 64)
    private String contentHash; // SHA-256

    private String integrationName; // from CSV

    private String boundType; // inbound, outbound, datamart, common

    private String propertyPrefix; // from CSV

    private boolean indexed;

    private int chunkCount;

    @Column(length = 2048)
    private String detectedConcepts; // comma-separated: "Gateway,Router,Channel"

    private LocalDateTime lastModified;

    private LocalDateTime indexedAt;
}
