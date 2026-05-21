package com.alip.assistant.service;

import com.alip.assistant.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvConfigParserServiceTest {

    @TempDir
    Path tempDir;

    private AppProperties appProperties;
    private CsvConfigParserService parser;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
    }

    private CsvConfigParserService createParser(Path csvFile) {
        appProperties.getIndexing().setCsvPath(csvFile.toString());
        return new CsvConfigParserService(appProperties);
    }

    @Test
    void parseConfiguration_validCsv_returnsAllEntries() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile, """
            Integration Name,XML Configuration File,Bound Type,Property Prefix,Status
            holdingInquiry,/path/to/file.xml,inbound,com.accenture.alip.server.ix.holdingInquiry,active
            publishEvent,/path/to/publish.xml,outbound,com.accenture.alip.server.ix.publishEvent,active
            """);

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).integrationName()).isEqualTo("holdingInquiry");
        assertThat(entries.get(0).xmlConfigPath()).isEqualTo("/path/to/file.xml");
        assertThat(entries.get(0).boundType()).isEqualTo("inbound");
        assertThat(entries.get(0).propertyPrefix()).isEqualTo("com.accenture.alip.server.ix.holdingInquiry");
        assertThat(entries.get(0).status()).isEqualTo("active");
        assertThat(entries.get(1).integrationName()).isEqualTo("publishEvent");
        assertThat(entries.get(1).boundType()).isEqualTo("outbound");
    }

    @Test
    void parseConfiguration_boundTypeNormalized_toLowercase() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile, """
            Integration Name,XML Configuration File,Bound Type,Property Prefix,Status
            test,/path/to/file.xml,Inbound,com.test.prefix,active
            """);

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).boundType()).isEqualTo("inbound");
    }

    @Test
    void parseConfiguration_skipsRowsWithFewerThan5Fields() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile, """
            Integration Name,XML Configuration File,Bound Type,Property Prefix,Status
            holdingInquiry,/path/to/file.xml,inbound,com.test.prefix,active
            incomplete,/path/to/file.xml,inbound
            another,/path/to/other.xml,outbound,com.test.other,active
            """);

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).integrationName()).isEqualTo("holdingInquiry");
        assertThat(entries.get(1).integrationName()).isEqualTo("another");
    }

    @Test
    void parseConfiguration_skipsRowsWithEmptyRequiredColumns() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile, """
            Integration Name,XML Configuration File,Bound Type,Property Prefix,Status
            ,/path/to/file.xml,inbound,com.test.prefix,active
            holdingInquiry,,inbound,com.test.prefix,active
            holdingInquiry,/path/to/file.xml,,com.test.prefix,active
            holdingInquiry,/path/to/file.xml,inbound,,active
            holdingInquiry,/path/to/file.xml,inbound,com.test.prefix,
            valid,/path/to/valid.xml,outbound,com.test.valid,active
            """);

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).integrationName()).isEqualTo("valid");
    }

    @Test
    void parseConfiguration_skipsEmptyLines() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile, """
            Integration Name,XML Configuration File,Bound Type,Property Prefix,Status
            
            holdingInquiry,/path/to/file.xml,inbound,com.test.prefix,active
            
            """);

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).hasSize(1);
    }

    @Test
    void parseConfiguration_headerOnly_returnsEmptyList() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile, "Integration Name,XML Configuration File,Bound Type,Property Prefix,Status\n");

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).isEmpty();
    }

    @Test
    void parseConfiguration_fileMissing_throwsRuntimeException() {
        Path csvFile = tempDir.resolve("nonexistent.txt");
        parser = createParser(csvFile);

        assertThatThrownBy(() -> parser.parseConfiguration())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void parseConfiguration_emptyFile_throwsRuntimeException() throws IOException {
        Path csvFile = tempDir.resolve("empty.txt");
        Files.writeString(csvFile, "");

        parser = createParser(csvFile);

        assertThatThrownBy(() -> parser.parseConfiguration())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void getCountByBoundType_categorizesByBoundType() {
        List<IntegrationEntry> entries = List.of(
            new IntegrationEntry("a", "/path/a.xml", "inbound", "com.a", "active"),
            new IntegrationEntry("b", "/path/b.xml", "inbound", "com.b", "active"),
            new IntegrationEntry("c", "/path/c.xml", "outbound", "com.c", "active"),
            new IntegrationEntry("d", "/path/d.xml", "datamart", "com.d", "active"),
            new IntegrationEntry("e", "/path/e.xml", "common", "com.e", "active")
        );

        parser = new CsvConfigParserService(appProperties);
        Map<String, Long> counts = parser.getCountByBoundType(entries);

        assertThat(counts).containsEntry("inbound", 2L);
        assertThat(counts).containsEntry("outbound", 1L);
        assertThat(counts).containsEntry("datamart", 1L);
        assertThat(counts).containsEntry("common", 1L);
    }

    @Test
    void getCountByBoundType_emptyList_returnsEmptyMap() {
        parser = new CsvConfigParserService(appProperties);
        Map<String, Long> counts = parser.getCountByBoundType(List.of());

        assertThat(counts).isEmpty();
    }

    @Test
    void parseConfiguration_realCsvFormat_parsesCorrectly() throws IOException {
        Path csvFile = tempDir.resolve("config.txt");
        Files.writeString(csvFile,
            "Integration Name,XML Configuration File,Bound Type,Property Prefix,Status\n" +
            "holdingInquiry,/home/dev/Trunk/trunk/interfaceexchange/apps/nep-integration-config/src/main/resources/realtime/inbound/ACORD_LA_HoldingInquiry_TC203_A2.36_Main.xml,inbound,com.accenture.alip.server.ix.holdingInquiry,active\n" +
            "publishEvent,/home/dev/Trunk/trunk/interfaceexchange/apps/nep-integration-config/src/main/resources/realtime/outbound/PublishEvent.xml,outbound,com.accenture.alip.server.ix.publishEvent,active\n");

        parser = createParser(csvFile);
        List<IntegrationEntry> entries = parser.parseConfiguration();

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).integrationName()).isEqualTo("holdingInquiry");
        assertThat(entries.get(0).xmlConfigPath()).contains("ACORD_LA_HoldingInquiry");
        assertThat(entries.get(1).integrationName()).isEqualTo("publishEvent");
        assertThat(entries.get(1).boundType()).isEqualTo("outbound");
    }
}
