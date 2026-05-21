package com.alip.assistant.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chunk of a document produced by the ChunkingService.
 * Each chunk contains content, metadata about its source, and fields
 * populated during later pipeline stages (concepts, score).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    private String id;
    private String content;
    private String fileName;
    private String filePath;
    private String fileType;
    private String module;
    private int chunkIndex;
    private String integrationName;
    private String boundType;
    @Builder.Default
    private List<String> concepts = new ArrayList<>();
    private String contentHash;
    private int startLine;
    private int endLine;
    private double score; // used during retrieval

    /**
     * Legacy embedding field retained for backward compatibility with VectorStore.
     * New code should use LangChain4j's EmbeddingStore which manages embeddings separately.
     * @deprecated Use LangChain4j InMemoryEmbeddingStore instead.
     */
    @Deprecated
    private float[] embedding;
}
