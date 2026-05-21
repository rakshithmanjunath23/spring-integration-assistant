package com.alip.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing file metadata for the file browser.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoDto {

    private Long id;
    private String fileName;
    private String absolutePath;
    private String relativePath;
    private String integrationName;
    private String boundType;
    private String fileType;
    private long fileSize;
    private String contentHash;
    private boolean indexed;
    private int chunkCount;
    private List<String> concepts;
}
