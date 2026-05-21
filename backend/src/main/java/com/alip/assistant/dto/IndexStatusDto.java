package com.alip.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing the current indexing job status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexStatusDto {

    private String jobId;
    private IndexingStatus status;
    private int totalFiles;
    private int indexedFiles;
    private int totalChunks;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /**
     * Possible states of the indexing process.
     */
    public enum IndexingStatus {
        IDLE,
        RUNNING,
        COMPLETE,
        ERROR
    }
}
