package com.alip.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a source citation in an assistant response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceCitation {

    private String filePath;
    private String integrationName;
    private double relevanceScore;
    private String lineRange;
}
