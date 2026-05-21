package com.alip.assistant.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps LangChain4j's EmbeddingModel (AllMiniLmL6V2, 384-dim local ONNX)
 * to generate vector embeddings for document chunks and queries.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final int DIMENSION = 384;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void init() {
        log.info("Initialized EmbeddingService with LangChain4j AllMiniLmL6V2 model (dimension={})", DIMENSION);
    }

    /**
     * Generate an embedding for a single text string.
     *
     * @param text the text to embed
     * @return the embedding vector, or null if the text is blank or an error occurs
     */
    public Embedding embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Cannot embed null or blank text, returning null");
            return null;
        }
        try {
            Response<Embedding> response = embeddingModel.embed(TextSegment.from(text));
            return response.content();
        } catch (Exception e) {
            log.error("Error generating embedding for text (length={}): {}", text.length(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generate embeddings for a batch of text strings.
     *
     * @param texts the list of texts to embed
     * @return list of embeddings (null entries for texts that failed)
     */
    public List<Embedding> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<TextSegment> segments = texts.stream()
                    .map(text -> TextSegment.from(text != null ? text : ""))
                    .toList();
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            return response.content();
        } catch (Exception e) {
            log.error("Error generating batch embeddings for {} texts: {}", texts.size(), e.getMessage(), e);
            // Fall back to individual embedding to salvage what we can
            List<Embedding> results = new ArrayList<>();
            for (String text : texts) {
                results.add(embed(text));
            }
            return results;
        }
    }

    /**
     * Returns the embedding dimension (384 for AllMiniLmL6V2).
     */
    public int getDimension() {
        return DIMENSION;
    }
}
