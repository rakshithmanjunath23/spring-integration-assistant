package com.alip.assistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for chat sessions.
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
}
