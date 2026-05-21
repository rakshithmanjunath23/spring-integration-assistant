package com.alip.assistant.util;

import com.alip.assistant.config.AppProperties;
import com.alip.assistant.exception.FileAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathValidatorTest {

    @TempDir
    Path tempDir;

    private PathValidator pathValidator;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getIndexing().setProjectRoot(tempDir.toString());
        pathValidator = new PathValidator(appProperties);
    }

    @Test
    void validateAndResolve_validPathWithinRoot_returnsResolvedPath() throws IOException {
        // Create a file within the project root
        Path subDir = Files.createDirectories(tempDir.resolve("src/main"));
        Path file = Files.createFile(subDir.resolve("Test.java"));

        Path result = pathValidator.validateAndResolve(file.toString());

        assertTrue(result.startsWith(tempDir));
        assertEquals(file.toRealPath(), result);
    }

    @Test
    void validateAndResolve_pathWithTraversalSequence_throwsException() {
        String maliciousPath = tempDir + "/../../../etc/passwd";

        FileAccessDeniedException ex = assertThrows(FileAccessDeniedException.class,
                () -> pathValidator.validateAndResolve(maliciousPath));

        assertTrue(ex.getMessage().contains("traversal"));
    }

    @Test
    void validateAndResolve_pathWithBackslashTraversal_throwsException() {
        String maliciousPath = tempDir + "\\..\\..\\etc\\passwd";

        FileAccessDeniedException ex = assertThrows(FileAccessDeniedException.class,
                () -> pathValidator.validateAndResolve(maliciousPath));

        assertTrue(ex.getMessage().contains("traversal"));
    }

    @Test
    void validateAndResolve_pathOutsideRoot_throwsException() {
        // Use a path that exists but is outside the project root
        String outsidePath = "/tmp";

        FileAccessDeniedException ex = assertThrows(FileAccessDeniedException.class,
                () -> pathValidator.validateAndResolve(outsidePath));

        assertTrue(ex.getMessage().contains("outside the allowed project scope"));
    }

    @Test
    void validateAndResolve_nullPath_throwsException() {
        assertThrows(FileAccessDeniedException.class,
                () -> pathValidator.validateAndResolve(null));
    }

    @Test
    void validateAndResolve_emptyPath_throwsException() {
        assertThrows(FileAccessDeniedException.class,
                () -> pathValidator.validateAndResolve(""));
    }

    @Test
    void validateAndResolve_blankPath_throwsException() {
        assertThrows(FileAccessDeniedException.class,
                () -> pathValidator.validateAndResolve("   "));
    }

    @Test
    void isPathSafe_validPathWithinRoot_returnsTrue() throws IOException {
        Path file = Files.createFile(tempDir.resolve("valid.txt"));

        assertTrue(PathValidator.isPathSafe(file.toString(), tempDir.toString()));
    }

    @Test
    void isPathSafe_pathWithTraversal_returnsFalse() {
        assertFalse(PathValidator.isPathSafe(tempDir + "/../secret", tempDir.toString()));
    }

    @Test
    void isPathSafe_pathOutsideRoot_returnsFalse() {
        assertFalse(PathValidator.isPathSafe("/tmp/outside", tempDir.toString()));
    }

    @Test
    void isPathSafe_nullPath_returnsFalse() {
        assertFalse(PathValidator.isPathSafe(null, tempDir.toString()));
    }

    @Test
    void isPathSafe_nullRoot_returnsFalse() {
        assertFalse(PathValidator.isPathSafe("/some/path", null));
    }

    @Test
    void isPathSafe_emptyPath_returnsFalse() {
        assertFalse(PathValidator.isPathSafe("", tempDir.toString()));
    }

    @Test
    void isPathSafe_emptyRoot_returnsFalse() {
        assertFalse(PathValidator.isPathSafe("/some/path", ""));
    }

    @Test
    void validateAndResolve_nonExistentPathWithinRoot_returnsNormalizedPath() {
        // Non-existent file but within the root (absolute path)
        String nonExistentPath = tempDir.resolve("nonexistent/file.txt").toString();

        Path result = pathValidator.validateAndResolve(nonExistentPath);

        assertTrue(result.startsWith(tempDir));
    }

    @Test
    void validateAndResolve_relativePathWithinRoot_returnsResolvedPath() throws IOException {
        // Create a file within the project root
        Files.createFile(tempDir.resolve("relative.txt"));

        Path result = pathValidator.validateAndResolve("relative.txt");

        assertTrue(result.startsWith(tempDir));
    }
}
