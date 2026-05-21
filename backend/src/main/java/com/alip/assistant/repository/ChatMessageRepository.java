package com.alip.assistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for chat messages.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findBySessionIdOrderByTimestampAsc(String sessionId);

    void deleteBySessionId(String sessionId);

    @Query("SELECT DISTINCT c.sessionId FROM ChatMessageEntity c ORDER BY c.timestamp DESC")
    List<String> findDistinctSessionIds();
}
