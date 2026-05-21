package com.alip.assistant.util;

import com.alip.assistant.config.AppProperties;
import com.alip.assistant.exception.FileAccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Validates file paths to prevent path traversal attacks.
 * Ensures all file access requests resolve to paths within the configured project root.
 */
@Slf4j
@Component
public class PathValidator {

    private final Path projectRoot;

    public PathValidator(AppProperties appProperties) {
        this.projectRoot = resolveCanonicalPath(Paths.get(appProperties.getIndexing().getProjectRoot()));
    }

    /**
     * Validates and resolves a requested file path, ensuring it is within the project root.
     *
     * @param requestedPath the file path requested by the user
     * @return the validated canonical path
     * @throws FileAccessDeniedException if the path is invalid or outside the project root
     */
    public Path validateAndResolve(String requestedPath) throws FileAccessDeniedException {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new FileAccessDeniedException("File path must not be empty", requestedPath);
        }

        // Reject paths containing traversal sequences before resolution
        if (containsTraversalSequence(requestedPath)) {
            log.warn("Path traversal attempt detected: {}", sanitizeForLog(requestedPath));
            throw new FileAccessDeniedException(
                    "Path contains traversal sequences and is outside the allowed scope",
                    requestedPath
            );
        }

        Path resolved;
        try {
            Path requested = Paths.get(requestedPath);
            // Resolve to canonical form (resolving symlinks and relative segments)
            if (Files.exists(requested)) {
                resolved = requested.toRealPath();
            } else {
                // For non-existent paths, normalize and resolve against project root if relative
                resolved = requested.isAbsolute()
                        ? requested.normalize()
                        : projectRoot.resolve(requested).normalize();
            }
        } catch (IOException e) {
            log.warn("Failed to resolve path: {}", sanitizeForLog(requestedPath));
            throw new FileAccessDeniedException(
                    "Unable to resolve the requested path",
                    requestedPath,
                    e
            );
        }

        // Validate the canonical path starts with the project root
        if (!resolved.startsWith(projectRoot)) {
            log.warn("Path outside project root: {} (root: {})", sanitizeForLog(requestedPath), projectRoot);
            throw new FileAccessDeniedException(
                    "The requested path is outside the allowed project scope",
                    requestedPath
            );
        }

        return resolved;
    }

    /**
     * Static utility method for testing path safety without Spring context.
     *
     * @param path        the path to validate
     * @param allowedRoot the allowed root directory
     * @return true if the path is safe (within the allowed root and contains no traversal sequences)
     */
    public static boolean isPathSafe(String path, String allowedRoot) {
        if (path == null || path.isBlank() || allowedRoot == null || allowedRoot.isBlank()) {
            return false;
        }

        // Reject traversal sequences
        if (containsTraversalSequence(path)) {
            return false;
        }

        try {
            Path rootPath = resolveCanonicalPath(Paths.get(allowedRoot));
            Path requestedPath = Paths.get(path);

            Path resolved;
            if (Files.exists(requestedPath)) {
                resolved = requestedPath.toRealPath();
            } else {
                resolved = requestedPath.isAbsolute()
                        ? requestedPath.normalize()
                        : rootPath.resolve(requestedPath).normalize();
            }

            return resolved.startsWith(rootPath);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if a path string contains traversal sequences.
     */
    private static boolean containsTraversalSequence(String path) {
        return path.contains("../") || path.contains("..\\");
    }

    /**
     * Resolves a path to its canonical form, falling back to normalized absolute path
     * if the path does not exist on the filesystem.
     */
    private static Path resolveCanonicalPath(Path path) {
        try {
            if (Files.exists(path)) {
                return path.toRealPath();
            }
        } catch (IOException e) {
            // Fall through to normalization
        }
        return path.toAbsolutePath().normalize();
    }

    /**
     * Sanitizes a path string for safe inclusion in log entries.
     */
    private static String sanitizeForLog(String path) {
        if (path == null) {
            return "null";
        }
        // Remove control characters and limit length for log safety
        return path.replaceAll("[\\x00-\\x1f]", "?")
                .substring(0, Math.min(path.length(), 200));
    }
}
