package com.alip.assistant.controller;

import com.alip.assistant.dto.FileInfoDto;
import com.alip.assistant.dto.IndexStatusDto;
import com.alip.assistant.service.FileIndexService;
import com.alip.assistant.util.PathValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST controller for file browsing, content retrieval, and indexing operations.
 * Supports paginated file listing, search, reindexing, and secure file content access.
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileIndexService fileIndexService;
    private final PathValidator pathValidator;

    /**
     * Get paginated file list, optionally filtered by search query.
     * 
     * GET /api/files?page=0&size=20 - paginated file list
     * GET /api/files?search=query - search files by name
     * 
     * Returns paginated response with content, totalElements, totalPages, number, size.
     */
    @GetMapping
    public ResponseEntity<Page<FileInfoDto>> listFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        if (search != null && !search.isBlank()) {
            List<FileInfoDto> results = fileIndexService.searchFiles(search);
            // Wrap search results in a Page for consistent response format
            int start = Math.min(page * size, results.size());
            int end = Math.min(start + size, results.size());
            List<FileInfoDto> pageContent = results.subList(start, end);
            Page<FileInfoDto> pagedResults = new PageImpl<>(pageContent, PageRequest.of(page, size), results.size());
            return ResponseEntity.ok(pagedResults);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<FileInfoDto> files = fileIndexService.getFiles(pageable);
        return ResponseEntity.ok(files);
    }

    /**
     * Get current indexing status.
     */
    @GetMapping("/status")
    public ResponseEntity<IndexStatusDto> getStatus() {
        return ResponseEntity.ok(fileIndexService.getStatus());
    }

    /**
     * Trigger a reindex operation.
     * Accepts a mode parameter: "full" or "incremental".
     * Returns the current IndexStatusDto reflecting the started job.
     */
    @PostMapping("/reindex")
    public ResponseEntity<IndexStatusDto> reindex(@RequestBody(required = false) Map<String, String> body) {
        String mode = "full";
        if (body != null && body.containsKey("mode")) {
            mode = body.get("mode");
        }

        IndexStatusDto status = fileIndexService.reindex(mode);
        return ResponseEntity.accepted().body(status);
    }

    /**
     * Get raw file content by absolute path.
     * Validates the path using PathValidator to prevent path traversal attacks.
     * Returns content with appropriate Content-Type.
     */
    @GetMapping("/content")
    public ResponseEntity<String> getContent(@RequestParam String path) {
        // Validate path to prevent traversal attacks
        Path validatedPath = pathValidator.validateAndResolve(path);

        if (!Files.exists(validatedPath) || !Files.isRegularFile(validatedPath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String content = Files.readString(validatedPath);
            MediaType contentType = determineContentType(validatedPath.getFileName().toString());
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(content);
        } catch (IOException e) {
            log.error("Failed to read file: {}", validatedPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Determines the appropriate Content-Type based on file extension.
     */
    private MediaType determineContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) {
            return MediaType.parseMediaType("text/x-java-source");
        } else if (lower.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        } else if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return MediaType.parseMediaType("text/yaml");
        } else if (lower.endsWith(".properties")) {
            return MediaType.parseMediaType("text/x-java-properties");
        } else if (lower.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else if (lower.endsWith(".md")) {
            return MediaType.parseMediaType("text/markdown");
        }
        return MediaType.TEXT_PLAIN;
    }
}
