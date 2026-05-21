package com.alip.assistant.service;

import com.alip.assistant.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileScannerServiceTest {

    @TempDir
    Path tempDir;

    private AppProperties appProperties;
    private FileScannerService fileScannerService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        fileScannerService = new FileScannerService(appProperties);
    }

    @Test
    void findScanRoot_findsNearestPomXmlAncestor() throws IOException {
        // Create: tempDir/project/pom.xml
        //         tempDir/project/src/main/resources/config.xml
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir.resolve("src/main/resources"));
        Files.writeString(projectDir.resolve("pom.xml"), "<project/>");
        Path configFile = projectDir.resolve("src/main/resources/config.xml");
        Files.writeString(configFile, "<beans/>");

        Path scanRoot = fileScannerService.findScanRoot(configFile);

        assertThat(scanRoot).isEqualTo(projectDir);
    }

    @Test
    void findScanRoot_returnsNullWhenNoPomXmlFound() throws IOException {
        Path noMavenDir = tempDir.resolve("nopom/src/main");
        Files.createDirectories(noMavenDir);
        Path file = noMavenDir.resolve("file.xml");
        Files.writeString(file, "<beans/>");

        Path scanRoot = fileScannerService.findScanRoot(file);

        assertThat(scanRoot).isNull();
    }

    @Test
    void discoverFiles_findsSupportedExtensions() throws IOException {
        Path projectDir = createProjectStructure();

        Files.writeString(projectDir.resolve("src/Main.java"), "class Main {}");
        Files.writeString(projectDir.resolve("src/config.xml"), "<beans/>");
        Files.writeString(projectDir.resolve("src/app.yaml"), "key: value");
        Files.writeString(projectDir.resolve("src/app.yml"), "key: value");
        Files.writeString(projectDir.resolve("src/app.properties"), "key=value");
        Files.writeString(projectDir.resolve("src/readme.txt"), "text");
        Files.writeString(projectDir.resolve("src/README.md"), "# Title");
        // Unsupported extension - should be excluded
        Files.writeString(projectDir.resolve("src/image.png"), "binary");

        Path configFile = projectDir.resolve("src/config.xml");
        List<Path> files = fileScannerService.discoverFiles(configFile.toString());

        assertThat(files).hasSize(7);
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("Main.java"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("config.xml"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("app.yaml"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("app.yml"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("app.properties"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("readme.txt"));
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("README.md"));
        assertThat(files).noneMatch(p -> p.getFileName().toString().equals("image.png"));
    }

    @Test
    void discoverFiles_excludesConfiguredDirectories() throws IOException {
        Path projectDir = createProjectStructure();

        // Create files in excluded directories
        Files.createDirectories(projectDir.resolve("target"));
        Files.writeString(projectDir.resolve("target/App.java"), "class App {}");
        Files.createDirectories(projectDir.resolve(".git"));
        Files.writeString(projectDir.resolve(".git/config.txt"), "git config");
        Files.createDirectories(projectDir.resolve("src/.svn"));
        Files.writeString(projectDir.resolve("src/.svn/entries.txt"), "svn");
        Files.createDirectories(projectDir.resolve("node_modules"));
        Files.writeString(projectDir.resolve("node_modules/index.js"), "module");
        Files.createDirectories(projectDir.resolve(".idea"));
        Files.writeString(projectDir.resolve(".idea/workspace.xml"), "<project/>");

        // Create a valid file
        Files.writeString(projectDir.resolve("src/Main.java"), "class Main {}");

        Path configFile = projectDir.resolve("src/Main.java");
        List<Path> files = fileScannerService.discoverFiles(configFile.toString());

        // Only the pom.xml and Main.java should be found (pom.xml is .xml extension)
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("Main.java"));
        assertThat(files).noneMatch(p -> p.toString().contains("target"));
        assertThat(files).noneMatch(p -> p.toString().contains(".git"));
        assertThat(files).noneMatch(p -> p.toString().contains(".svn"));
        assertThat(files).noneMatch(p -> p.toString().contains("node_modules"));
        assertThat(files).noneMatch(p -> p.toString().contains(".idea"));
    }

    @Test
    void discoverFiles_skipsFilesExceedingMaxSize() throws IOException {
        Path projectDir = createProjectStructure();

        // Set a very small max file size for testing
        appProperties.getIndexing().setMaxFileSize(100);

        Files.writeString(projectDir.resolve("src/small.java"), "class Small {}");
        // Create a file larger than 100 bytes
        Files.writeString(projectDir.resolve("src/large.java"), "x".repeat(200));

        Path configFile = projectDir.resolve("src/small.java");
        List<Path> files = fileScannerService.discoverFiles(configFile.toString());

        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("small.java"));
        assertThat(files).noneMatch(p -> p.getFileName().toString().equals("large.java"));
    }

    @Test
    void discoverFiles_returnsEmptyListForNonexistentPath() {
        List<Path> files = fileScannerService.discoverFiles("/nonexistent/path/config.xml");

        assertThat(files).isEmpty();
    }

    @Test
    void discoverFiles_returnsEmptyListWhenNoPomXmlFound() throws IOException {
        Path noMavenDir = tempDir.resolve("nopom/src");
        Files.createDirectories(noMavenDir);
        Path configFile = noMavenDir.resolve("config.xml");
        Files.writeString(configFile, "<beans/>");

        List<Path> files = fileScannerService.discoverFiles(configFile.toString());

        assertThat(files).isEmpty();
    }

    @Test
    void discoverFiles_respectsMaxDepth() throws IOException {
        Path projectDir = createProjectStructure();

        // Set max depth to 2 (project root + 1 level)
        appProperties.getIndexing().setMaxDepth(2);

        // Create files at different depths
        Files.writeString(projectDir.resolve("src/Shallow.java"), "class Shallow {}");
        Files.createDirectories(projectDir.resolve("src/deep/nested"));
        Files.writeString(projectDir.resolve("src/deep/nested/Deep.java"), "class Deep {}");

        Path configFile = projectDir.resolve("src/Shallow.java");
        List<Path> files = fileScannerService.discoverFiles(configFile.toString());

        // At maxDepth=2: root(0) -> src(1) -> files(2), but deep/nested(3) is too deep
        assertThat(files).anyMatch(p -> p.getFileName().toString().equals("Shallow.java"));
        assertThat(files).noneMatch(p -> p.getFileName().toString().equals("Deep.java"));
    }

    @Test
    void discoverFiles_doesNotFollowSymbolicLinks() throws IOException {
        Path projectDir = createProjectStructure();
        Files.writeString(projectDir.resolve("src/Main.java"), "class Main {}");

        // Create a symlink to an external directory
        Path externalDir = tempDir.resolve("external");
        Files.createDirectories(externalDir);
        Files.writeString(externalDir.resolve("Secret.java"), "class Secret {}");

        try {
            Path symlink = projectDir.resolve("src/linked");
            Files.createSymbolicLink(symlink, externalDir);

            Path configFile = projectDir.resolve("src/Main.java");
            List<Path> files = fileScannerService.discoverFiles(configFile.toString());

            assertThat(files).noneMatch(p -> p.getFileName().toString().equals("Secret.java"));
        } catch (UnsupportedOperationException | IOException e) {
            // Symlinks may not be supported on all platforms - skip test
        }
    }

    /**
     * Helper to create a basic Maven project structure with pom.xml and src directory.
     */
    private Path createProjectStructure() throws IOException {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir.resolve("src"));
        Files.writeString(projectDir.resolve("pom.xml"), "<project/>");
        return projectDir;
    }
}
