package com.alip.assistant.rag;

import com.alip.assistant.dto.SourceCitation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Represents a fully assembled prompt ready to be sent to the LLM.
 * Contains all components: system prompt, context from retrieved chunks,
 * conversation history, user query, citations, and token estimate.
 */
@Getter
@Setter
@AllArgsConstructor
public class AssembledPrompt {
    private String systemPrompt;
    private String contextSection;
    private List<Map<String, String>> conversationHistory;
    private String userQuery;
    private List<SourceCitation> citations;
    private int estimatedTokens;
}
