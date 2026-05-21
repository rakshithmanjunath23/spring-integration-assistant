package com.alip.assistant.service;

import com.alip.assistant.dto.ChatRequest;
import com.alip.assistant.dto.ChatResponse;
import com.alip.assistant.dto.ChatStreamEvent;
import com.alip.assistant.dto.SourceCitation;
import com.alip.assistant.rag.AssembledPrompt;
import com.alip.assistant.rag.PromptAssemblerService;
import com.alip.assistant.repository.ChatMessageEntity;
import com.alip.assistant.repository.ChatSessionEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full chat pipeline:
 * get/create session → assemble prompt → stream LLM → save messages.
 *
 * Supports both streaming (SSE) and non-streaming chat modes.
 * Generates follow-up question suggestions and handles no-context scenarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final GrokChatService grokChatService;
    private final PromptAssemblerService promptAssemblerService;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;

    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "What integration patterns are used in this project?",
            "Can you explain the error handling strategy?",
            "What message channels are configured?"
    );

    private static final String NO_CONTEXT_NOTICE =
            "I couldn't find relevant context in the indexed files for your query. "
            + "Try refining your question, selecting specific files from the sidebar, "
            + "or asking about a specific integration name (e.g., holdingInquiry, newBusiness).";

    /**
     * Stream chat response as a Flux of ChatStreamEvent.
     * Pipeline: get/create session → assemble prompt → stream LLM → emit events → save messages.
     *
     * @param sessionId     the session UUID (may be null to create new)
     * @param message       the user's message
     * @param selectedFiles list of file paths the user has selected for priority
     * @return a Flux of ChatStreamEvent (TOKEN, CITATIONS, SUGGESTIONS, DONE)
     */
    public Flux<ChatStreamEvent> streamChat(String sessionId, String message, List<String> selectedFiles) {
        return Flux.defer(() -> {
            // Step 1: Get or create session
            ChatSessionEntity session = chatSessionService.getOrCreateSession(sessionId);
            String activeSessionId = session.getId();

            // Step 2: Get conversation history for prompt assembly
            List<ChatMessageEntity> history = chatSessionService.getRecentHistory(activeSessionId);

            // Step 3: Assemble prompt with RAG context
            AssembledPrompt assembledPrompt = promptAssemblerService.assemble(
                    message, selectedFiles != null ? selectedFiles : Collections.emptyList(), history);

            // Step 4: Check for no-context scenario
            List<SourceCitation> citations = assembledPrompt.citations();
            if (citations == null || citations.isEmpty()) {
                return handleNoContextStream(activeSessionId, message, selectedFiles);
            }

            // Step 5: Save user message
            saveUserMessage(activeSessionId, message, selectedFiles);

            // Step 6: Stream LLM response and collect tokens
            StringBuilder responseBuilder = new StringBuilder();
            String messageId = UUID.randomUUID().toString();

            return grokChatService.streamChat(assembledPrompt)
                    .map(token -> {
                        responseBuilder.append(token);
                        return ChatStreamEvent.token(token);
                    })
                    .concatWith(Flux.defer(() -> {
                        // After stream completes, emit citations, suggestions, and done events
                        String fullResponse = responseBuilder.toString();

                        // Save assistant message
                        saveAssistantMessage(activeSessionId, fullResponse, citations);

                        // Generate follow-up suggestions
                        List<String> suggestions = generateSuggestions(message, fullResponse);

                        return Flux.just(
                                ChatStreamEvent.citations(citations),
                                ChatStreamEvent.suggestions(suggestions),
                                ChatStreamEvent.done(activeSessionId, messageId)
                        );
                    }))
                    .onErrorResume(error -> {
                        log.error("Error during streaming chat: {}", error.getMessage(), error);
                        return Flux.just(ChatStreamEvent.error(
                                "An error occurred while generating the response. Please try again."));
                    });
        });
    }

    /**
     * Non-streaming chat for simple responses.
     *
     * @param request the chat request containing message, sessionId, and selectedFiles
     * @return a Mono containing the complete ChatResponse
     */
    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.defer(() -> {
            String sessionId = request.getSessionId();
            String message = request.getMessage();
            List<String> selectedFiles = request.getSelectedFiles();

            // Step 1: Get or create session
            ChatSessionEntity session = chatSessionService.getOrCreateSession(sessionId);
            String activeSessionId = session.getId();

            // Step 2: Get conversation history
            List<ChatMessageEntity> history = chatSessionService.getRecentHistory(activeSessionId);

            // Step 3: Assemble prompt
            AssembledPrompt assembledPrompt = promptAssemblerService.assemble(
                    message, selectedFiles != null ? selectedFiles : Collections.emptyList(), history);

            // Step 4: Check for no-context scenario
            List<SourceCitation> citations = assembledPrompt.citations();
            if (citations == null || citations.isEmpty()) {
                return handleNoContextMono(activeSessionId, message, selectedFiles);
            }

            // Step 5: Save user message
            saveUserMessage(activeSessionId, message, selectedFiles);

            // Step 6: Call LLM (non-streaming)
            return grokChatService.chat(assembledPrompt)
                    .map(responseText -> {
                        // Save assistant message
                        saveAssistantMessage(activeSessionId, responseText, citations);

                        // Generate suggestions
                        List<String> suggestions = generateSuggestions(message, responseText);

                        return ChatResponse.builder()
                                .id(UUID.randomUUID().toString())
                                .sessionId(activeSessionId)
                                .role("assistant")
                                .message(responseText)
                                .citations(citations)
                                .suggestedQuestions(suggestions)
                                .timestamp(LocalDateTime.now())
                                .build();
                    })
                    .onErrorResume(error -> {
                        log.error("Error during non-streaming chat: {}", error.getMessage(), error);
                        return Mono.just(ChatResponse.builder()
                                .id(UUID.randomUUID().toString())
                                .sessionId(activeSessionId)
                                .role("assistant")
                                .message("An error occurred while generating the response. Please try again.")
                                .timestamp(LocalDateTime.now())
                                .build());
                    });
        });
    }

    /**
     * Get conversation history for a session.
     *
     * @param sessionId the session UUID
     * @return list of ChatResponse objects representing the conversation
     */
    public List<ChatResponse> getHistory(String sessionId) {
        return chatSessionService.getConversationHistory(sessionId).stream()
                .map(entity -> ChatResponse.builder()
                        .id(entity.getId().toString())
                        .sessionId(entity.getSessionId())
                        .message(entity.getContent())
                        .role(entity.getRole())
                        .citations(deserializeCitations(entity.getCitationsJson()))
                        .timestamp(entity.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Delete a session and all associated messages.
     *
     * @param sessionId the session UUID
     */
    public void deleteSession(String sessionId) {
        chatSessionService.deleteSession(sessionId);
    }

    /**
     * Get all session IDs.
     *
     * @return list of session ID strings
     */
    public List<String> getSessions() {
        return chatSessionService.getAllSessions().stream()
                .map(ChatSessionEntity::getId)
                .collect(Collectors.toList());
    }

    /**
     * Handle the no-context scenario for streaming: return a notice suggesting query refinement.
     */
    private Flux<ChatStreamEvent> handleNoContextStream(String sessionId, String message,
                                                         List<String> selectedFiles) {
        saveUserMessage(sessionId, message, selectedFiles);
        saveAssistantMessage(sessionId, NO_CONTEXT_NOTICE, Collections.emptyList());

        String messageId = UUID.randomUUID().toString();
        List<String> suggestions = List.of(
                "What integrations are available in this project?",
                "Can you list the inbound integration flows?",
                "What files are indexed?"
        );

        return Flux.just(
                ChatStreamEvent.token(NO_CONTEXT_NOTICE),
                ChatStreamEvent.citations(Collections.emptyList()),
                ChatStreamEvent.suggestions(suggestions),
                ChatStreamEvent.done(sessionId, messageId)
        );
    }

    /**
     * Handle the no-context scenario for non-streaming: return a notice suggesting query refinement.
     */
    private Mono<ChatResponse> handleNoContextMono(String sessionId, String message,
                                                    List<String> selectedFiles) {
        saveUserMessage(sessionId, message, selectedFiles);
        saveAssistantMessage(sessionId, NO_CONTEXT_NOTICE, Collections.emptyList());

        List<String> suggestions = List.of(
                "What integrations are available in this project?",
                "Can you list the inbound integration flows?",
                "What files are indexed?"
        );

        return Mono.just(ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .role("assistant")
                .message(NO_CONTEXT_NOTICE)
                .citations(Collections.emptyList())
                .suggestedQuestions(suggestions)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * Generate 2-4 suggested follow-up questions based on the user's query and the response.
     * Uses heuristics based on the content to suggest relevant follow-ups.
     */
    private List<String> generateSuggestions(String userMessage, String responseText) {
        List<String> suggestions = new ArrayList<>();

        // Extract integration names mentioned in the response for targeted suggestions
        String lowerResponse = responseText.toLowerCase();
        String lowerMessage = userMessage.toLowerCase();

        if (lowerResponse.contains("channel") || lowerResponse.contains("flow")) {
            suggestions.add("What are the error handling channels in this flow?");
        }
        if (lowerResponse.contains("gateway") || lowerResponse.contains("service-activator")) {
            suggestions.add("How does the gateway handle timeouts and errors?");
        }
        if (lowerResponse.contains("router") || lowerResponse.contains("splitter")) {
            suggestions.add("What routing conditions are configured?");
        }
        if (lowerResponse.contains("jms") || lowerResponse.contains("kafka")) {
            suggestions.add("What is the message retry and dead-letter strategy?");
        }
        if (lowerResponse.contains("bean") || lowerResponse.contains("configuration")) {
            suggestions.add("What dependencies does this bean have?");
        }

        // Always include a general follow-up
        if (!lowerMessage.contains("error")) {
            suggestions.add("What error handling is configured for this integration?");
        }
        if (!lowerMessage.contains("diagram") && !lowerMessage.contains("flow")) {
            suggestions.add("Can you show the message flow as a diagram?");
        }

        // Limit to 2-4 suggestions
        if (suggestions.isEmpty()) {
            return DEFAULT_SUGGESTIONS.subList(0, Math.min(3, DEFAULT_SUGGESTIONS.size()));
        }
        return suggestions.subList(0, Math.min(4, suggestions.size()));
    }

    /**
     * Save a user message to the session.
     */
    private void saveUserMessage(String sessionId, String message, List<String> selectedFiles) {
        String selectedFilesJson = null;
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            try {
                selectedFilesJson = objectMapper.writeValueAsString(selectedFiles);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize selected files: {}", e.getMessage());
            }
        }
        chatSessionService.saveMessage(sessionId, "user", message, null, selectedFilesJson);
    }

    /**
     * Save an assistant message to the session.
     */
    private void saveAssistantMessage(String sessionId, String content, List<SourceCitation> citations) {
        String citationsJson = null;
        if (citations != null && !citations.isEmpty()) {
            try {
                citationsJson = objectMapper.writeValueAsString(citations);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize citations: {}", e.getMessage());
            }
        }
        chatSessionService.saveMessage(sessionId, "assistant", content, citationsJson, null);
    }

    /**
     * Deserialize citations JSON string back to a list of SourceCitation objects.
     */
    private List<SourceCitation> deserializeCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(citationsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SourceCitation.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize citations: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
