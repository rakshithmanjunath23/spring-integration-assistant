package com.alip.assistant.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j configuration providing embedding model, vector store,
 * and LLM chat model beans for the RAG pipeline.
 */
@Configuration
public class LangChain4jConfig {

    /**
     * Local ONNX embedding model (all-MiniLM-L6-v2, 384 dimensions).
     * No external API calls required for embeddings.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * In-memory vector store for document chunk embeddings.
     */
    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * Streaming chat model using xAI Grok via OpenAI-compatible endpoint.
     * Used for SSE streaming responses to the frontend.
     */
    @Bean
    public StreamingChatLanguageModel streamingChatModel(AppProperties props) {
        AppProperties.Grok grok = props.getGrok();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(grok.getApiUrl())
                .apiKey(grok.getApiKey())
                .modelName(grok.getModel())
                .temperature(grok.getTemperature())
                .maxTokens(grok.getMaxTokens())
                .timeout(Duration.ofSeconds(grok.getTimeoutSeconds()))
                .build();
    }

    /**
     * Non-streaming chat model for synchronous LLM calls.
     */
    @Bean
    public ChatLanguageModel chatModel(AppProperties props) {
        AppProperties.Grok grok = props.getGrok();
        return OpenAiChatModel.builder()
                .baseUrl(grok.getApiUrl())
                .apiKey(grok.getApiKey())
                .modelName(grok.getModel())
                .temperature(grok.getTemperature())
                .maxTokens(grok.getMaxTokens())
                .timeout(Duration.ofSeconds(grok.getTimeoutSeconds()))
                .build();
    }
}
