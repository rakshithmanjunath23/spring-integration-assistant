package com.alip.assistant.service;

import com.alip.assistant.config.AppProperties;
import com.alip.assistant.repository.FileMetadataEntity;
import com.alip.assistant.repository.FileMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for extracting file metadata and tracking content hashes for incremental re-indexing.
 * Works with FileScannerService to process discovered files and persist their metadata.
 */
@Service
public class FileMetadataService {

    private static final Logger log = LoggerFactory.getLogger(FileMetadataService.class);

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final FileMetadataRepository fileMetadataRepository;
    private final AppProperties appProperties;

    public FileMetadataService(FileMetadataRepository fileMetadataRepository,
                               AppProperties appProperties) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.appProperties = appProperties;
    }

    /**
     * Extract metadata from a file and persist it to the database.
     * Skips files exceeding 10MB or that are unreadable due to permission/encoding errors.
     * Only processes files whose content hash has changed (incremental re-indexing support).
     *
     * @param filePath        the absolute path to the source file
     * @param integrationName the integration name from the CSV configuration
     * @param boundType       the bound type (inbound, outbound, datamart, common)
     * @param propertyPrefix  the property prefix from the CSV configuration
     * @return the persisted FileMetadataEntity, or null if the file was skipped
     */
    @Transactional
    public FileMetadataEntity extractAndPersist(Path filePath, String integrationName,
                                                 String boundType, String propertyPrefix) {
        String absolutePath = filePath.toAbsolutePath().normalize().toString();

        // Check file size
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            log.warn("Skipping unreadable file (cannot determine size): {} - {}", absolutePath, e.getMessage());
            return null;
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            log.warn("Skipping file exceeding 10MB limit ({} bytes): {}", fileSize, absolutePath);
            return null;
        }

        // Read file content
        String content;
        try {
            content = Files.readString(filePath);
        } catch (MalformedInputException e) {
            log.warn("Skipping file with encoding error: {} - {}", absolutePath, e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("Skipping unreadable file (permission/IO error): {} - {}", absolutePath, e.getMessage());
            return null;
        }

        // Compute SHA-256 hash
        String currentHash = computeSha256Hash(content);

        // Check if file has changed (incremental re-indexing)
        if (!hasFileChanged(absolutePath, currentHash)) {
            log.debug("File unchanged, skipping: {}", absolutePath);
            // Return existing entity without re-processing
            return fileMetadataRepository.findByAbsolutePath(absolutePath).orElse(null);
        }

        // Extract metadata
        String fileName = filePath.getFileName().toString();
        String fileExtension = extractExtension(fileName);
        String relativePath = computeRelativePath(filePath);
        LocalDateTime lastModified = getLastModifiedTime(filePath);

        // Find or create entity
        FileMetadataEntity entity = fileMetadataRepository.findByAbsolutePath(absolutePath)
                .orElse(new FileMetadataEntity());

        entity.setAbsolutePath(absolutePath);
        entity.setRelativePath(relativePath);
        entity.setFileName(fileName);
        entity.setFileType(fileExtension);
        entity.setFileSize(fileSize);
        entity.setContentHash(currentHash);
        entity.setIntegrationName(integrationName);
        entity.setBoundType(boundType);
        entity.setPropertyPrefix(propertyPrefix);
        entity.setLastModified(lastModified);
        entity.setIndexed(false); // Will be set to true after chunking/embedding
        entity.setIndexedAt(null); // Reset until re-indexed

        return fileMetadataRepository.save(entity);
    }

    /**
     * Compute the full SHA-256 hash of the given content string.
     *
     * @param content the file content to hash
     * @return the full SHA-256 hash as a lowercase hex string (64 characters)
     */
    public String computeSha256Hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Check if a file's content has changed by comparing the current hash with the stored hash.
     *
     * @param absolutePath the absolute path of the file
     * @param currentHash  the current SHA-256 hash of the file content
     * @return true if the file is new or its hash has changed, false if unchanged
     */
    public boolean hasFileChanged(String absolutePath, String currentHash) {
        Optional<FileMetadataEntity> existing = fileMetadataRepository.findByAbsolutePath(absolutePath);
        if (existing.isEmpty()) {
            return true; // New file, needs processing
        }
        return !currentHash.equals(existing.get().getContentHash());
    }

    /**
     * Remove metadata entries for files that no longer exist on disk.
     * Used during re-indexing to clean up stale entries from deleted files.
     *
     * @param currentPaths the set of absolute paths that currently exist
     */
    @Transactional
    public void removeStaleEntries(Set<String> currentPaths) {
        List<FileMetadataEntity> allEntries = fileMetadataRepository.findAll();
        List<FileMetadataEntity> staleEntries = allEntries.stream()
                .filter(entity -> !currentPaths.contains(entity.getAbsolutePath()))
                .collect(Collectors.toList());

        if (!staleEntries.isEmpty()) {
            log.info("Removing {} stale file metadata entries", staleEntries.size());
            fileMetadataRepository.deleteAll(staleEntries);
        }
    }

    /**
     * Clear all file metadata entries. Used for full re-index operations.
     */
    @Transactional
    public void clearAll() {
        long count = fileMetadataRepository.count();
        fileMetadataRepository.deleteAll();
        log.info("Cleared {} file metadata entries for full re-index", count);
    }

    /**
     * Extract the file extension without the leading dot.
     */
    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * Compute the relative path from the configured project root.
     */
    private String computeRelativePath(Path filePath) {
        try {
            Path projectRoot = Path.of(appProperties.getIndexing().getProjectRoot())
                    .toAbsolutePath().normalize();
            Path absolute = filePath.toAbsolutePath().normalize();
            if (absolute.startsWith(projectRoot)) {
                return projectRoot.relativize(absolute).toString();
            }
        } catch (Exception e) {
            log.debug("Could not compute relative path for: {}", filePath);
        }
        return filePath.getFileName().toString();
    }

    /**
     * Get the last modified time of a file.
     */
    private LocalDateTime getLastModifiedTime(Path filePath) {
        try {
            return LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(filePath).toInstant(),
                    ZoneId.systemDefault());
        } catch (IOException e) {
            log.debug("Could not read last modified time for: {}", filePath);
            return LocalDateTime.now();
        }
    }
}
