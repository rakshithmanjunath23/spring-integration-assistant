package com.alip.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Server-Sent Event payload for streaming chat responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {

    private EventType type;
    private String content;
    private List<SourceCitation> citations;
    private List<String> questions;
    private String sessionId;
    private String messageId;

    /**
     * Types of streaming events sent to the client.
     */
    public enum EventType {
        TOKEN,
        CITATIONS,
        SUGGESTIONS,
        DONE,
        ERROR
    }

    public static ChatStreamEvent token(String content) {
        return ChatStreamEvent.builder()
                .type(EventType.TOKEN)
                .content(content)
                .build();
    }

    public static ChatStreamEvent citations(List<SourceCitation> citations) {
        return ChatStreamEvent.builder()
                .type(EventType.CITATIONS)
                .citations(citations)
                .build();
    }

    public static ChatStreamEvent suggestions(List<String> questions) {
        return ChatStreamEvent.builder()
                .type(EventType.SUGGESTIONS)
                .questions(questions)
                .build();
    }

    public static ChatStreamEvent done(String sessionId, String messageId) {
        return ChatStreamEvent.builder()
                .type(EventType.DONE)
                .sessionId(sessionId)
                .messageId(messageId)
                .build();
    }

    public static ChatStreamEvent error(String errorMessage) {
        return ChatStreamEvent.builder()
                .type(EventType.ERROR)
                .content(errorMessage)
                .build();
    }
}
