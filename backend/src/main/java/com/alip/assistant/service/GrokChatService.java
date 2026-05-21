package com.alip.assistant.service;

import com.alip.assistant.rag.AssembledPrompt;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for interacting with the Grok LLM via LangChain4j.
 * Supports both streaming (SSE) and non-streaming responses.
 * Implements retry logic with exponential backoff for transient failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrokChatService {

    private final StreamingChatLanguageModel streamingChatModel;
    private final ChatLanguageModel chatModel;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);

    /**
     * Stream chat tokens as a reactive Flux from the assembled prompt.
     * Supports cancellation when the subscriber (SSE connection) disconnects.
     * Implements retry logic: 3 attempts with exponential backoff (1s, 2s, 4s).
     *
     * @param prompt the assembled prompt containing system, context, history, and user query
     * @return a Flux of token strings streamed from the LLM
     */
    public Flux<String> streamChat(AssembledPrompt prompt) {
        return Flux.defer(() -> createStreamingFlux(prompt))
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, INITIAL_BACKOFF)
                        .filter(this::isRetryableError)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying Grok streaming request (attempt {}): {}",
                                signal.totalRetries() + 1, signal.failure().getMessage()))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnSubscribe(sub -> log.debug("Starting Grok streaming request (estimated {}t)",
                        prompt.estimatedTokens()))
                .doOnError(e -> log.error("Grok streaming failed after retries: {}", e.getMessage()));
    }

    /**
     * Non-streaming chat for simple responses.
     * Implements retry logic: 3 attempts with exponential backoff.
     *
     * @param prompt the assembled prompt
     * @return a Mono containing the complete response text
     */
    public Mono<String> chat(AssembledPrompt prompt) {
        return Mono.fromCallable(() -> {
                    List<ChatMessage> messages = buildMessageList(prompt);
                    log.debug("Sending non-streaming request to Grok (estimated {}t)", prompt.estimatedTokens());

                    ChatResponse response = chatModel.chat(messages);
                    String content = response.aiMessage().text();

                    logTokenUsage(response);
                    return content;
                })
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, INITIAL_BACKOFF)
                        .filter(this::isRetryableError)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying Grok chat request (attempt {}): {}",
                                signal.totalRetries() + 1, signal.failure().getMessage()))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnError(e -> log.error("Grok chat failed after retries: {}", e.getMessage()));
    }

    /**
     * Creates the streaming Flux that connects to the LLM and emits tokens.
     * Handles cancellation by tracking subscriber state.
     */
    private Flux<String> createStreamingFlux(AssembledPrompt prompt) {
        return Flux.create(sink -> {
            List<ChatMessage> messages = buildMessageList(prompt);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            // Handle subscriber cancellation (SSE connection closed)
            sink.onDispose(() -> {
                cancelled.set(true);
                log.debug("Grok streaming request cancelled by subscriber");
            });

            streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (cancelled.get()) {
                        return; // Stop processing if subscriber cancelled
                    }
                    if (partialResponse != null && !partialResponse.isEmpty()) {
                        sink.next(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (!cancelled.get()) {
                        logTokenUsage(response);
                        sink.complete();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (!cancelled.get()) {
                        log.error("Grok streaming error: {}", error.getMessage());
                        sink.error(error);
                    }
                }
            });
        });
    }

    /**
     * Build the LangChain4j ChatMessage list from the assembled prompt.
     * Order: SystemMessage → context + history as user/assistant messages → UserMessage
     */
    private List<ChatMessage> buildMessageList(AssembledPrompt prompt) {
        List<ChatMessage> messages = new ArrayList<>();

        // System message with instructions
        messages.add(SystemMessage.from(prompt.systemPrompt()));

        // Context section as a user message (if present)
        if (prompt.contextSection() != null && !prompt.contextSection().isEmpty()) {
            messages.add(UserMessage.from(prompt.contextSection()));
        }

        // Conversation history
        if (prompt.conversationHistory() != null) {
            for (Map<String, String> historyMsg : prompt.conversationHistory()) {
                String role = historyMsg.get("role");
                String content = historyMsg.get("content");
                if (content == null || content.isEmpty()) continue;

                if ("assistant".equals(role)) {
                    messages.add(AiMessage.from(content));
                } else {
                    messages.add(UserMessage.from(content));
                }
            }
        }

        // Current user query
        messages.add(UserMessage.from(prompt.userQuery()));

        return messages;
    }

    /**
     * Determine if an error is retryable (timeout, 503, connection errors).
     */
    private boolean isRetryableError(Throwable error) {
        if (error instanceof TimeoutException) {
            return true;
        }
        if (error instanceof ConnectException) {
            return true;
        }
        String message = error.getMessage();
        if (message != null) {
            // Check for HTTP 503 Service Unavailable
            if (message.contains("503") || message.contains("Service Unavailable")) {
                return true;
            }
            // Check for connection-related errors
            if (message.contains("Connection refused") || message.contains("Connection reset")
                    || message.contains("timeout") || message.contains("Timeout")) {
                return true;
            }
        }
        // Check cause chain
        if (error.getCause() != null && error.getCause() != error) {
            return isRetryableError(error.getCause());
        }
        return false;
    }

    /**
     * Log token usage from the LLM response.
     */
    private void logTokenUsage(ChatResponse response) {
        if (response != null && response.metadata() != null && response.metadata().tokenUsage() != null) {
            var usage = response.metadata().tokenUsage();
            log.info("Grok token usage - prompt: {}, completion: {}, total: {}",
                    usage.inputTokenCount(),
                    usage.outputTokenCount(),
                    usage.totalTokenCount());
        }
    }
}
