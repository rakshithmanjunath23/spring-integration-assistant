package com.alip.assistant.service;

import com.alip.assistant.exception.SessionNotFoundException;
import com.alip.assistant.repository.ChatMessageEntity;
import com.alip.assistant.repository.ChatMessageRepository;
import com.alip.assistant.repository.ChatSessionEntity;
import com.alip.assistant.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing chat sessions and their messages.
 * Provides CRUD operations for sessions and message persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    private static final int MAX_HISTORY_MESSAGES = 20;

    /**
     * Create a new chat session with a UUID v4 identifier.
     *
     * @return the newly created ChatSessionEntity
     */
    @Transactional
    public ChatSessionEntity createSession() {
        ChatSessionEntity session = ChatSessionEntity.builder()
                .id(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .messageCount(0)
                .build();

        ChatSessionEntity saved = chatSessionRepository.save(session);
        log.debug("Created new chat session: {}", saved.getId());
        return saved;
    }

    /**
     * Get a session by its ID.
     *
     * @param sessionId the session UUID
     * @return the ChatSessionEntity
     * @throws SessionNotFoundException if the session does not exist
     */
    public ChatSessionEntity getSession(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    /**
     * Get a session by ID, or create a new one if the provided ID is null or not found.
     *
     * @param sessionId the session UUID (may be null)
     * @return an existing or newly created ChatSessionEntity
     */
    @Transactional
    public ChatSessionEntity getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession();
        }
        return chatSessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    log.debug("Session '{}' not found, creating new session", sessionId);
                    ChatSessionEntity session = ChatSessionEntity.builder()
                            .id(sessionId)
                            .createdAt(LocalDateTime.now())
                            .lastMessageAt(LocalDateTime.now())
                            .messageCount(0)
                            .build();
                    return chatSessionRepository.save(session);
                });
    }

    /**
     * Save a message (user or assistant) to a session.
     * Updates the session's lastMessageAt and messageCount.
     *
     * @param sessionId      the session UUID
     * @param role           "user" or "assistant"
     * @param content        the message content
     * @param citationsJson  optional JSON string of citations (for assistant messages)
     * @param selectedFilesJson optional JSON string of selected file paths (for user messages)
     * @return the persisted ChatMessageEntity
     */
    @Transactional
    public ChatMessageEntity saveMessage(String sessionId, String role, String content,
                                          String citationsJson, String selectedFilesJson) {
        ChatMessageEntity message = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .citationsJson(citationsJson)
                .selectedFilesJson(selectedFilesJson)
                .timestamp(LocalDateTime.now())
                .build();

        ChatMessageEntity saved = chatMessageRepository.save(message);

        // Update session metadata
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastMessageAt(LocalDateTime.now());
            session.setMessageCount(session.getMessageCount() + 1);
            chatSessionRepository.save(session);
        });

        log.debug("Saved {} message to session {}", role, sessionId);
        return saved;
    }

    /**
     * Get the full conversation history for a session, ordered by timestamp ascending.
     *
     * @param sessionId the session UUID
     * @return list of messages ordered by timestamp
     */
    public List<ChatMessageEntity> getConversationHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    /**
     * Get the most recent conversation history for a session, limited to MAX_HISTORY_MESSAGES.
     * Used for prompt assembly to keep context within token budget.
     *
     * @param sessionId the session UUID
     * @return list of the most recent messages (up to 20), ordered by timestamp ascending
     */
    public List<ChatMessageEntity> getRecentHistory(String sessionId) {
        List<ChatMessageEntity> allMessages = chatMessageRepository
                .findBySessionIdOrderByTimestampAsc(sessionId);

        if (allMessages == null || allMessages.isEmpty()) {
            return Collections.emptyList();
        }

        int start = Math.max(0, allMessages.size() - MAX_HISTORY_MESSAGES);
        return allMessages.subList(start, allMessages.size());
    }

    /**
     * Delete a session and all its associated messages.
     *
     * @param sessionId the session UUID
     */
    @Transactional
    public void deleteSession(String sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
        log.info("Deleted session {} and all associated messages", sessionId);
    }

    /**
     * Get all sessions, ordered by most recent activity.
     *
     * @return list of all ChatSessionEntity objects
     */
    public List<ChatSessionEntity> getAllSessions() {
        return chatSessionRepository.findAll();
    }
}
