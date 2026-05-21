package com.alip.assistant.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service wrapping LangChain4j's InMemoryEmbeddingStore to manage
 * document chunk storage, retrieval, and lifecycle operations.
 */
@Slf4j
@Service
public class VectorStoreService {

    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingService embeddingService;

    /**
     * Tracks stored embedding IDs by file path for removal support.
     * Key: filePath, Value: set of embedding IDs stored for that file.
     */
    private final Map<String, Set<String>> filePathToEmbeddingIds = new ConcurrentHashMap<>();

    /**
     * Tracks stored chunks by their deduplication key (contentHash + chunkIndex)
     * to prevent duplicate insertions.
     */
    private final Set<String> storedChunkKeys = ConcurrentHashMap.newKeySet();

    public VectorStoreService(InMemoryEmbeddingStore<TextSegment> embeddingStore,
                              EmbeddingService embeddingService) {
        this.embeddingStore = embeddingStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Embed and store document chunks in the vector store.
     * Prevents duplicates by checking contentHash + chunkIndex before insertion.
     *
     * @param chunks the document chunks to add
     */
    public void addChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        int added = 0;
        int skipped = 0;

        for (DocumentChunk chunk : chunks) {
            String deduplicationKey = buildDeduplicationKey(chunk);

            // Skip duplicates
            if (deduplicationKey != null && storedChunkKeys.contains(deduplicationKey)) {
                skipped++;
                continue;
            }

            // Generate embedding
            Embedding embedding = embeddingService.embed(chunk.getContent());
            if (embedding == null) {
                log.warn("Skipping chunk (file={}, index={}) - embedding generation failed",
                        chunk.getFilePath(), chunk.getChunkIndex());
                continue;
            }

            // Build TextSegment with metadata
            TextSegment segment = buildTextSegment(chunk);

            // Store in embedding store
            String embeddingId = embeddingStore.add(embedding, segment);

            // Track for file-based removal
            String filePath = chunk.getFilePath();
            if (filePath != null) {
                filePathToEmbeddingIds
                        .computeIfAbsent(filePath, k -> ConcurrentHashMap.newKeySet())
                        .add(embeddingId);
            }

            // Track for deduplication
            if (deduplicationKey != null) {
                storedChunkKeys.add(deduplicationKey);
            }

            added++;
        }

        log.info("Added {} chunks to vector store ({} duplicates skipped)", added, skipped);
    }

    /**
     * Search the vector store for chunks relevant to the given query.
     *
     * @param query         the search query text
     * @param maxResults    maximum number of results to return (1-50, default 10)
     * @param threshold     minimum similarity score threshold (0.0-1.0)
     * @param priorityFiles list of file paths to boost in results
     * @param boostFactor   score multiplier for priority file chunks (default 1.5)
     * @return list of matching DocumentChunks with scores, ordered by descending similarity
     */
    public List<DocumentChunk> search(String query, int maxResults, double threshold,
                                      List<String> priorityFiles, double boostFactor) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // Clamp maxResults to valid range
        maxResults = Math.max(1, Math.min(50, maxResults));

        // Generate query embedding
        Embedding queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding == null) {
            log.warn("Could not generate embedding for query, returning empty results");
            return Collections.emptyList();
        }

        // Search with a larger result set to allow for boosting and re-ranking
        int searchLimit = Math.min(maxResults * 3, 50);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(searchLimit)
                .minScore(threshold)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        // Convert matches to DocumentChunks with score boosting
        Set<String> priorityFileSet = priorityFiles != null
                ? new HashSet<>(priorityFiles)
                : Collections.emptySet();

        List<DocumentChunk> results = matches.stream()
                .map(match -> {
                    DocumentChunk chunk = toDocumentChunk(match);
                    // Apply priority boost
                    if (!priorityFileSet.isEmpty() && chunk.getFilePath() != null
                            && priorityFileSet.contains(chunk.getFilePath())) {
                        chunk.setScore(match.score() * boostFactor);
                    } else {
                        chunk.setScore(match.score());
                    }
                    return chunk;
                })
                // Re-sort by boosted score descending
                .sorted(Comparator.comparingDouble(DocumentChunk::getScore).reversed())
                // Apply final threshold filter (boosted scores may still be above threshold)
                .filter(chunk -> chunk.getScore() >= threshold)
                .limit(maxResults)
                .collect(Collectors.toList());

        log.debug("Vector search returned {} results for query (length={})", results.size(), query.length());
        return results;
    }

    /**
     * Remove all stored chunks for a given file path.
     *
     * @param filePath the absolute file path whose chunks should be removed
     */
    public void removeByFilePath(String filePath) {
        if (filePath == null) {
            return;
        }

        Set<String> embeddingIds = filePathToEmbeddingIds.remove(filePath);
        if (embeddingIds != null && !embeddingIds.isEmpty()) {
            embeddingIds.forEach(embeddingStore::remove);
            log.info("Removed {} chunks for file: {}", embeddingIds.size(), filePath);
        }

        // Remove deduplication keys for this file
        storedChunkKeys.removeIf(key -> key.startsWith(filePath + ":"));
    }

    /**
     * Clear the entire vector store and all tracking data.
     */
    public void clear() {
        // Remove all known IDs
        Set<String> allIds = filePathToEmbeddingIds.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        allIds.forEach(embeddingStore::remove);

        filePathToEmbeddingIds.clear();
        storedChunkKeys.clear();
        log.info("Cleared vector store");
    }

    /**
     * Get the total number of stored chunks.
     */
    public int size() {
        return filePathToEmbeddingIds.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * Convenience search method using default parameters.
     * Uses default topK=10, threshold=0.7, boostFactor=1.5.
     *
     * @param query         the search query text
     * @param priorityFiles list of file paths to boost in results
     * @return list of matching DocumentChunks with scores
     */
    public List<DocumentChunk> search(String query, List<String> priorityFiles) {
        return search(query, 10, 0.7, priorityFiles, 1.5);
    }

    // --- Private helpers ---

    private String buildDeduplicationKey(DocumentChunk chunk) {
        if (chunk.getContentHash() == null) {
            return null;
        }
        return chunk.getFilePath() + ":" + chunk.getContentHash() + ":" + chunk.getChunkIndex();
    }

    private TextSegment buildTextSegment(DocumentChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        putIfNotNull(metadata, "filePath", chunk.getFilePath());
        putIfNotNull(metadata, "fileName", chunk.getFileName());
        putIfNotNull(metadata, "fileType", chunk.getFileType());
        putIfNotNull(metadata, "integrationName", chunk.getIntegrationName());
        putIfNotNull(metadata, "boundType", chunk.getBoundType());
        metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
        metadata.put("startLine", String.valueOf(chunk.getStartLine()));
        metadata.put("endLine", String.valueOf(chunk.getEndLine()));
        putIfNotNull(metadata, "contentHash", chunk.getContentHash());

        if (chunk.getConcepts() != null && !chunk.getConcepts().isEmpty()) {
            metadata.put("concepts", String.join(",", chunk.getConcepts()));
        }

        dev.langchain4j.data.document.Metadata langchainMetadata =
                dev.langchain4j.data.document.Metadata.from(metadata);

        return TextSegment.from(chunk.getContent(), langchainMetadata);
    }

    private DocumentChunk toDocumentChunk(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        dev.langchain4j.data.document.Metadata metadata = segment.metadata();

        List<String> concepts = Collections.emptyList();
        String conceptsStr = metadata.getString("concepts");
        if (conceptsStr != null && !conceptsStr.isBlank()) {
            concepts = Arrays.asList(conceptsStr.split(","));
        }

        return DocumentChunk.builder()
                .content(segment.text())
                .filePath(metadata.getString("filePath"))
                .fileName(metadata.getString("fileName"))
                .fileType(metadata.getString("fileType"))
                .integrationName(metadata.getString("integrationName"))
                .boundType(metadata.getString("boundType"))
                .chunkIndex(parseIntSafe(metadata.getString("chunkIndex")))
                .startLine(parseIntSafe(metadata.getString("startLine")))
                .endLine(parseIntSafe(metadata.getString("endLine")))
                .contentHash(metadata.getString("contentHash"))
                .concepts(concepts)
                .score(match.score())
                .build();
    }

    private void putIfNotNull(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private int parseIntSafe(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
