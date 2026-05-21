package com.alip.assistant.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single parsed row from the IXconfiguration.txt CSV file.
 * Each entry describes one integration definition with its configuration path,
 * bound type, property prefix, and status.
 */
@Getter
@Setter
@AllArgsConstructor
public class IntegrationEntry {
    private String integrationName;
    private String xmlConfigPath;
    private String boundType;
    private String propertyPrefix;
    private String status;
}
