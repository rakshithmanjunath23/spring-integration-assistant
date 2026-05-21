package com.alip.assistant.controller;

import com.alip.assistant.dto.ChatRequest;
import com.alip.assistant.dto.ChatResponse;
import com.alip.assistant.dto.ChatStreamEvent;
import com.alip.assistant.service.ChatOrchestratorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST controller for chat operations including streaming SSE responses,
 * session management, and message history.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ObjectMapper objectMapper;

    /**
     * Non-streaming chat endpoint.
     * Accepts a ChatRequest with message (max 4000 chars), optional sessionId, and optional selectedFiles.
     * Returns a ChatResponse with the assistant's reply and sessionId.
     */
    @PostMapping
    public Mono<ResponseEntity<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        return chatOrchestratorService.chat(request)
                .map(ResponseEntity::ok);
    }

    /**
     * SSE streaming chat endpoint.
     * Returns a Flux of ServerSentEvent with named events: token, citations, suggestions, done, error.
     * Each event carries JSON data with a type field matching the event name.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam(required = false) String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) List<String> selectedFiles) {

        return chatOrchestratorService.streamChat(sessionId, message, selectedFiles)
                .map(event -> {
                    String eventName = event.getType().name().toLowerCase();
                    return buildSseEvent(eventName, event);
                })
                .onErrorResume(e -> {
                    log.error("Error during chat stream", e);
                    ChatStreamEvent errorEvent = ChatStreamEvent.error(e.getMessage());
                    return Flux.just(buildSseEvent("error", errorEvent));
                });
    }

    /**
     * Get ordered message history for a session.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatResponse>> getHistory(@RequestParam String sessionId) {
        return ResponseEntity.ok(chatOrchestratorService.getHistory(sessionId));
    }

    /**
     * Get list of all chat sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getSessions() {
        return ResponseEntity.ok(chatOrchestratorService.getSessions());
    }

    /**
     * Delete a chat session and all its messages.
     * Returns HTTP 204 No Content on success.
     */
    @DeleteMapping("/session/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String sessionId) {
        chatOrchestratorService.deleteSession(sessionId);
    }

    /**
     * Builds a named ServerSentEvent with JSON-serialized data.
     */
    private ServerSentEvent<String> buildSseEvent(String eventName, ChatStreamEvent event) {
        String data;
        try {
            data = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize SSE event", e);
            data = "{\"type\":\"ERROR\",\"content\":\"Serialization error\"}";
        }
        return ServerSentEvent.<String>builder()
                .event(eventName)
                .data(data)
                .build();
    }
}
