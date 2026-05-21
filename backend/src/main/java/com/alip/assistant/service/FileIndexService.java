package com.alip.assistant.service;

import com.alip.assistant.config.AppConfig;
import com.alip.assistant.dto.FileInfoDto;
import com.alip.assistant.dto.IndexStatusDto;
import com.alip.assistant.rag.ChunkingService;
import com.alip.assistant.rag.DocumentChunk;
import com.alip.assistant.rag.VectorStoreService;
import com.alip.assistant.repository.FileMetadataEntity;
import com.alip.assistant.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileIndexService {

    private final AppConfig appConfig;
    private final FileMetadataRepository fileMetadataRepository;
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(
            Arrays.asList(".java", ".xml", ".yaml", ".yml", ".properties", ".txt", ".md")
    );

    private final AtomicReference<IndexStatusDto> currentStatus = new AtomicReference<>(
            IndexStatusDto.builder().status(IndexStatusDto.IndexingStatus.IDLE).build()
    );

    public IndexStatusDto getStatus() {
        IndexStatusDto status = currentStatus.get();
        status.setTotalFiles((int) fileMetadataRepository.count());
        status.setIndexedFiles((int) fileMetadataRepository.countByIndexedTrue());
        status.setTotalChunks(vectorStoreService.size());
        return status;
    }

    public List<FileInfoDto> getAllFiles() {
        return fileMetadataRepository.findAll().stream()
                .map(this::toFileInfoDto)
                .collect(Collectors.toList());
    }

    /**
     * Get paginated file list.
     */
    public Page<FileInfoDto> getFiles(Pageable pageable) {
        return fileMetadataRepository.findAll(pageable)
                .map(this::toFileInfoDto);
    }

    public List<FileInfoDto> searchFiles(String query) {
        return fileMetadataRepository.findByFileNameContainingIgnoreCase(query).stream()
                .map(this::toFileInfoDto)
                .collect(Collectors.toList());
    }

    public String getFileContent(String path) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("File not found: " + path);
        }
        Path allowedBase = Paths.get(appConfig.getSourceProjectPath()).toAbsolutePath().normalize();
        Path resolved = filePath.toAbsolutePath().normalize();
        if (!resolved.startsWith(allowedBase)) {
            throw new SecurityException("Access denied: path outside allowed directory");
        }
        return new String(Files.readAllBytes(filePath));
    }

    /**
     * Trigger reindex with mode (full or incremental).
     * Returns the IndexStatusDto reflecting the started job.
     */
    public IndexStatusDto reindex(String mode) {
        String jobId = UUID.randomUUID().toString();
        IndexStatusDto status = IndexStatusDto.builder()
                .jobId(jobId)
                .status(IndexStatusDto.IndexingStatus.RUNNING)
                .totalFiles(0)
                .indexedFiles(0)
                .startedAt(LocalDateTime.now())
                .build();
        currentStatus.set(status);

        if ("incremental".equalsIgnoreCase(mode)) {
            reindexIncremental();
        } else {
            reindexFull();
        }

        return currentStatus.get();
    }

    @Async
    public void reindexFull() {
        log.info("Starting full reindex...");

        try {
            vectorStoreService.clear();
            scanAndIndexFiles();
            indexCsvConfig();

            currentStatus.set(IndexStatusDto.builder()
                    .status(IndexStatusDto.IndexingStatus.COMPLETE)
                    .completedAt(LocalDateTime.now())
                    .build());
            log.info("Reindex complete. Total chunks: {}", vectorStoreService.size());
        } catch (Exception e) {
            log.error("Reindex failed", e);
            currentStatus.set(IndexStatusDto.builder()
                    .status(IndexStatusDto.IndexingStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    @Async
    public void reindexIncremental() {
        log.info("Starting incremental reindex...");

        try {
            scanAndIndexFiles();
            indexCsvConfig();

            currentStatus.set(IndexStatusDto.builder()
                    .status(IndexStatusDto.IndexingStatus.COMPLETE)
                    .completedAt(LocalDateTime.now())
                    .build());
            log.info("Incremental reindex complete. Total chunks: {}", vectorStoreService.size());
        } catch (Exception e) {
            log.error("Incremental reindex failed", e);
            currentStatus.set(IndexStatusDto.builder()
                    .status(IndexStatusDto.IndexingStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * @deprecated Use {@link #reindex(String)} instead.
     */
    @Deprecated
    @Async
    public void reindex() {
        reindex("full");
    }

    private void scanAndIndexFiles() throws IOException {
        Path basePath = Paths.get(appConfig.getSourceProjectPath());
        if (!Files.exists(basePath)) {
            log.warn("Source path does not exist: {}", basePath);
            return;
        }

        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                String extension = getExtension(fileName);

                if (SUPPORTED_EXTENSIONS.contains(extension)) {
                    try {
                        indexFile(file, attrs);
                    } catch (Exception e) {
                        log.warn("Failed to index file: {}", file, e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void indexFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Skip files larger than 200KB to prevent OOM
        if (attrs.size() > 200_000) {
            log.debug("Skipping large file: {} ({}KB)", file.getFileName(), attrs.size() / 1024);
            return;
        }

        String absolutePath = file.toAbsolutePath().toString();
        String content = new String(Files.readAllBytes(file));
        String hash = computeHash(content);

        Optional<FileMetadataEntity> existing = fileMetadataRepository.findByAbsolutePath(absolutePath);
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            return;
        }

        String fileName = file.getFileName().toString();
        String extension = getExtension(fileName).replace(".", "");
        String relativePath = Paths.get(appConfig.getSourceProjectPath())
                .relativize(file).toString();

        FileMetadataEntity entity = existing.orElse(new FileMetadataEntity());
        entity.setFileName(fileName);
        entity.setAbsolutePath(absolutePath);
        entity.setRelativePath(relativePath);
        entity.setFileType(extension);
        entity.setFileSize(attrs.size());
        entity.setLastModified(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()));
        entity.setContentHash(hash);
        entity.setIndexed(true);
        entity.setIndexedAt(LocalDateTime.now());
        entity.setDetectedConcepts(extractIntegrationChains(content, extension));
        fileMetadataRepository.save(entity);

        List<DocumentChunk> chunks = chunkingService.chunkFile(content, fileName, absolutePath, extension, null);
        for (DocumentChunk chunk : chunks) {
            chunk.setContentHash(hash);
        }
        vectorStoreService.addChunks(chunks);
        
        entity.setChunkCount(chunks.size());
        fileMetadataRepository.save(entity);

        log.debug("Indexed: {} ({} chunks)", fileName, chunks.size());
    }

    private void indexCsvConfig() {
        try {
            Path csvPath = Paths.get(appConfig.getIxConfigCsvPath());
            if (Files.exists(csvPath)) {
                String content = new String(Files.readAllBytes(csvPath));
                List<DocumentChunk> chunks = chunkingService.chunkFile(
                        content, "IXconfiguration.txt", csvPath.toString(), "txt", "config");
                for (DocumentChunk chunk : chunks) {
                    chunk.setContentHash(computeHash(chunk.getContent()));
                }
                vectorStoreService.addChunks(chunks);
                log.info("Indexed IX configuration CSV ({} chunks)", chunks.size());
            }
        } catch (Exception e) {
            log.warn("Failed to index CSV config", e);
        }
    }

    private String extractIntegrationChains(String content, String fileType) {
        List<String> chains = new ArrayList<>();
        if ("java".equals(fileType)) {
            if (content.contains("IntegrationFlow")) chains.add("IntegrationFlow");
            if (content.contains("@Bean")) chains.add("Bean");
            if (content.contains("ChildConfiguration")) chains.add("ChildConfiguration");
            if (content.contains("MessageChannel")) chains.add("MessageChannel");
        } else if ("xml".equals(fileType)) {
            if (content.contains("int:channel")) chains.add("Channel");
            if (content.contains("int:gateway")) chains.add("Gateway");
            if (content.contains("int:router")) chains.add("Router");
            if (content.contains("int:service-activator")) chains.add("ServiceActivator");
            if (content.contains("int-jms:")) chains.add("JMS");
            if (content.contains("int-http:")) chains.add("HTTP");
            if (content.contains("int-ws:")) chains.add("WebService");
        }
        return String.join(",", chains);
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private FileInfoDto toFileInfoDto(FileMetadataEntity entity) {
        List<String> concepts = entity.getDetectedConcepts() != null && !entity.getDetectedConcepts().isEmpty()
                ? Arrays.asList(entity.getDetectedConcepts().split(","))
                : List.of();
        return FileInfoDto.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .absolutePath(entity.getAbsolutePath())
                .relativePath(entity.getRelativePath())
                .integrationName(entity.getIntegrationName())
                .boundType(entity.getBoundType())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .contentHash(entity.getContentHash())
                .indexed(entity.isIndexed())
                .chunkCount(entity.getChunkCount())
                .concepts(concepts)
                .build();
    }
}
