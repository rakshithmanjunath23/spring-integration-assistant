package com.alip.assistant.service;

import com.alip.assistant.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Service for recursive file discovery within integration project directories.
 * Given an integration entry's XML config file path, discovers all relevant source files
 * by walking the directory tree from the nearest ancestor containing a pom.xml.
 *
 * Respects configurable exclusion rules, max depth, max file size, and does not follow
 * symbolic links.
 */
@Service
public class FileScannerService {

    private static final Logger log = LoggerFactory.getLogger(FileScannerService.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".xml", ".yaml", ".yml", ".properties", ".txt", ".md"
    );

    private final AppProperties appProperties;

    public FileScannerService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Discover all relevant source files starting from the scan root derived from the
     * given XML configuration file path.
     *
     * The scan root is the nearest ancestor directory containing a pom.xml file.
     * Files are discovered recursively up to the configured max depth, excluding
     * configured directories and respecting file size limits.
     *
     * @param xmlConfigPath the absolute path to an integration's XML configuration file
     * @return list of discovered file paths matching supported extensions
     */
    public List<Path> discoverFiles(String xmlConfigPath) {
        Path configPath = Paths.get(xmlConfigPath).toAbsolutePath().normalize();

        if (!Files.exists(configPath)) {
            log.warn("XML config file does not exist: {}", xmlConfigPath);
            return Collections.emptyList();
        }

        Path scanRoot = findScanRoot(configPath);
        if (scanRoot == null) {
            log.warn("Could not find scan root (no pom.xml ancestor) for: {}", xmlConfigPath);
            return Collections.emptyList();
        }

        log.debug("Scanning from root: {} for config: {}", scanRoot, xmlConfigPath);

        int maxDepth = appProperties.getIndexing().getMaxDepth();
        long maxFileSize = appProperties.getIndexing().getMaxFileSize();
        Set<String> excludeDirs = new HashSet<>(appProperties.getIndexing().getExcludeDirs());

        List<Path> discoveredFiles = new ArrayList<>();

        try {
            Files.walkFileTree(scanRoot, EnumSet.noneOf(FileVisitOption.class), maxDepth,
                    new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                                throws IOException {
                            // Skip symbolic link directories
                            if (Files.isSymbolicLink(dir)) {
                                log.debug("Skipping symbolic link directory: {}", dir);
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            // Skip excluded directories
                            String dirName = dir.getFileName() != null
                                    ? dir.getFileName().toString()
                                    : "";
                            if (excludeDirs.contains(dirName)) {
                                log.debug("Skipping excluded directory: {}", dir);
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            // Skip symbolic links
                            if (Files.isSymbolicLink(file)) {
                                log.debug("Skipping symbolic link file: {}", file);
                                return FileVisitResult.CONTINUE;
                            }

                            // Check file extension
                            String fileName = file.getFileName().toString();
                            String extension = getExtension(fileName);
                            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                                return FileVisitResult.CONTINUE;
                            }

                            // Check file size
                            if (attrs.size() > maxFileSize) {
                                log.warn("Skipping file exceeding max size ({} bytes > {} bytes): {}",
                                        attrs.size(), maxFileSize, file);
                                return FileVisitResult.CONTINUE;
                            }

                            // Check readability
                            if (!Files.isReadable(file)) {
                                log.warn("Skipping unreadable file: {}", file);
                                return FileVisitResult.CONTINUE;
                            }

                            discoveredFiles.add(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            log.warn("Skipping file due to access error: {} ({})",
                                    file, exc.getMessage());
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            log.error("Error during file discovery from root {}: {}", scanRoot, e.getMessage(), e);
        }

        log.info("Discovered {} files from scan root: {}", discoveredFiles.size(), scanRoot);
        return discoveredFiles;
    }

    /**
     * Find the scan root directory for a given file path.
     * The scan root is the nearest ancestor directory that contains a pom.xml file.
     * Traverses upward from the file's parent directory until a pom.xml is found
     * or the filesystem root is reached.
     *
     * @param filePath the path to start searching from (typically an XML config file)
     * @return the nearest ancestor directory containing pom.xml, or null if none found
     */
    public Path findScanRoot(Path filePath) {
        Path current = filePath.toAbsolutePath().normalize();

        // Start from the parent directory if the path is a file
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }

        while (current != null) {
            Path pomFile = current.resolve("pom.xml");
            if (Files.exists(pomFile) && Files.isRegularFile(pomFile)) {
                return current;
            }
            current = current.getParent();
        }

        return null;
    }

    /**
     * Extract the file extension including the leading dot.
     *
     * @param fileName the file name
     * @return the extension (e.g., ".java") or empty string if none
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }
}
