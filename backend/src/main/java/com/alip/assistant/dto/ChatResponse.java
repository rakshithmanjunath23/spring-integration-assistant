package com.alip.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for non-streaming chat responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String id;
    private String sessionId;
    private String role;
    private String message;
    private List<SourceCitation> citations;
    private List<String> suggestedQuestions;
    private LocalDateTime timestamp;
}
