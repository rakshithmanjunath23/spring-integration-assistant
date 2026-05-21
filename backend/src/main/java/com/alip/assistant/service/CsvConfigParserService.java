package com.alip.assistant.service;

import com.alip.assistant.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses the IXconfiguration.txt CSV file to discover integration definitions.
 * The CSV format is comma-delimited with a header row:
 * Integration Name, XML Configuration File, Bound Type, Property Prefix, Status
 */
@Service
public class CsvConfigParserService {

    private static final Logger log = LoggerFactory.getLogger(CsvConfigParserService.class);
    private static final int REQUIRED_COLUMNS = 5;

    private final AppProperties appProperties;

    public CsvConfigParserService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Parse the IXconfiguration.txt CSV file and return valid integration entries.
     * Skips rows with fewer than 5 fields or empty required columns.
     * Throws RuntimeException if the file is missing or unreadable.
     * Logs a warning and returns empty list if only the header row is present.
     *
     * @return list of valid IntegrationEntry records
     */
    public List<IntegrationEntry> parseConfiguration() {
        String csvPath = appProperties.getIndexing().getCsvPath();
        Path path = Path.of(csvPath);

        if (!Files.exists(path)) {
            throw new RuntimeException(
                "CSV configuration file not found: " + csvPath +
                ". Ensure the 'assistant.indexing.csv-path' property points to a valid IXconfiguration.txt file.");
        }

        if (!Files.isReadable(path)) {
            throw new RuntimeException(
                "CSV configuration file is not readable: " + csvPath +
                ". Check file permissions for the application process.");
        }

        List<IntegrationEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException(
                    "CSV configuration file is empty: " + csvPath);
            }

            String line;
            int rowNumber = 1; // 1-based, starting after header
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) {
                    continue;
                }

                String[] fields = line.split(",", -1);

                if (fields.length < REQUIRED_COLUMNS) {
                    log.warn("Row {}: Skipping row with fewer than {} fields. Raw content: {}",
                        rowNumber, REQUIRED_COLUMNS, line);
                    continue;
                }

                String integrationName = fields[0].trim();
                String xmlConfigPath = fields[1].trim();
                String boundType = fields[2].trim();
                String propertyPrefix = fields[3].trim();
                String status = fields[4].trim();

                if (integrationName.isEmpty() || xmlConfigPath.isEmpty() ||
                    boundType.isEmpty() || propertyPrefix.isEmpty() || status.isEmpty()) {
                    log.warn("Row {}: Skipping row with empty required column(s). Raw content: {}",
                        rowNumber, line);
                    continue;
                }

                entries.add(new IntegrationEntry(
                    integrationName,
                    xmlConfigPath,
                    boundType.toLowerCase(),
                    propertyPrefix,
                    status
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read CSV configuration file: " + csvPath + ". Error: " + e.getMessage(), e);
        }

        if (entries.isEmpty()) {
            log.warn("CSV configuration file contains only a header row (no data): {}", csvPath);
            return entries;
        }

        // Report totals by bound type
        Map<String, Long> countByBoundType = getCountByBoundType(entries);
        log.info("Discovered {} total integrations from CSV: {}", entries.size(), csvPath);
        countByBoundType.forEach((boundType, count) ->
            log.info("  Bound Type '{}': {} integration(s)", boundType, count));

        return entries;
    }

    /**
     * Categorize integration entries by their bound type and return the count for each.
     *
     * @param entries list of integration entries
     * @return map of bound type to count
     */
    public Map<String, Long> getCountByBoundType(List<IntegrationEntry> entries) {
        return entries.stream()
            .collect(Collectors.groupingBy(IntegrationEntry::boundType, Collectors.counting()));
    }
}
