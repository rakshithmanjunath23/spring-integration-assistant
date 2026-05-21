package com.alip.assistant.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreServiceTest {

    private VectorStoreService vectorStoreService;
    private EmbeddingService embeddingService;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void setUp() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingService = new EmbeddingService(model);
        vectorStoreService = new VectorStoreService(embeddingStore, embeddingService);
    }

    @Test
    void shouldAddChunksToStore() {
        List<DocumentChunk> chunks = List.of(
                createChunk("Spring Integration gateway config", "/path/file.xml", "hash1", 0),
                createChunk("Message channel definition", "/path/file.xml", "hash2", 1)
        );

        vectorStoreService.addChunks(chunks);
        assertEquals(2, vectorStoreService.size());
    }

    @Test
    void shouldPreventDuplicateChunks() {
        DocumentChunk chunk = createChunk("Spring Integration gateway", "/path/file.xml", "hash1", 0);

        vectorStoreService.addChunks(List.of(chunk));
        vectorStoreService.addChunks(List.of(chunk)); // duplicate

        assertEquals(1, vectorStoreService.size());
    }

    @Test
    void shouldSearchByQuery() {
        List<DocumentChunk> chunks = List.of(
                createChunk("Spring Integration gateway handles inbound HTTP requests", "/path/gateway.xml", "h1", 0),
                createChunk("Database connection pool configuration for PostgreSQL", "/path/db.properties", "h2", 0)
        );

        vectorStoreService.addChunks(chunks);

        List<DocumentChunk> results = vectorStoreService.search(
                "gateway HTTP request", 10, 0.0, null, 1.5);

        assertFalse(results.isEmpty());
        // The gateway chunk should be more relevant to the query
        assertEquals("/path/gateway.xml", results.get(0).getFilePath());
    }

    @Test
    void shouldRespectMaxResults() {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chunks.add(createChunk("Spring Integration content " + i, "/path/file" + i + ".xml", "hash" + i, 0));
        }

        vectorStoreService.addChunks(chunks);

        List<DocumentChunk> results = vectorStoreService.search(
                "Spring Integration", 3, 0.0, null, 1.5);

        assertTrue(results.size() <= 3);
    }

    @Test
    void shouldRespectThreshold() {
        List<DocumentChunk> chunks = List.of(
                createChunk("Spring Integration gateway", "/path/gateway.xml", "h1", 0),
                createChunk("Completely unrelated content about cooking recipes", "/path/cooking.txt", "h2", 0)
        );

        vectorStoreService.addChunks(chunks);

        // High threshold should filter out low-relevance results
        List<DocumentChunk> results = vectorStoreService.search(
                "Spring Integration gateway", 10, 0.8, null, 1.5);

        // All results should have score >= threshold
        for (DocumentChunk result : results) {
            assertTrue(result.getScore() >= 0.8,
                    "Score " + result.getScore() + " should be >= 0.8");
        }
    }

    @Test
    void shouldBoostPriorityFiles() {
        List<DocumentChunk> chunks = List.of(
                createChunk("Spring Integration message flow", "/path/a.xml", "h1", 0),
                createChunk("Spring Integration message routing", "/path/b.xml", "h2", 0)
        );

        vectorStoreService.addChunks(chunks);

        // Search with priority on b.xml
        List<DocumentChunk> results = vectorStoreService.search(
                "Spring Integration message", 10, 0.0, List.of("/path/b.xml"), 1.5);

        assertFalse(results.isEmpty());
        // b.xml should be boosted to top
        assertEquals("/path/b.xml", results.get(0).getFilePath());
    }

    @Test
    void shouldRemoveChunksByFilePath() {
        vectorStoreService.addChunks(List.of(
                createChunk("Content A about Spring", "/path/a.xml", "h1", 0)
        ));
        vectorStoreService.addChunks(List.of(
                createChunk("Content B about Java", "/path/b.xml", "h2", 0)
        ));

        assertEquals(2, vectorStoreService.size());

        vectorStoreService.removeByFilePath("/path/a.xml");
        assertEquals(1, vectorStoreService.size());
    }

    @Test
    void shouldClearAllChunks() {
        vectorStoreService.addChunks(List.of(
                createChunk("Content A", "/path/a.xml", "h1", 0),
                createChunk("Content B", "/path/b.xml", "h2", 0)
        ));

        assertEquals(2, vectorStoreService.size());

        vectorStoreService.clear();
        assertEquals(0, vectorStoreService.size());
    }

    @Test
    void shouldReturnEmptyForBlankQuery() {
        vectorStoreService.addChunks(List.of(
                createChunk("Content", "/path/file.xml", "h1", 0)
        ));

        List<DocumentChunk> results = vectorStoreService.search("", 10, 0.0, null, 1.5);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullQuery() {
        vectorStoreService.addChunks(List.of(
                createChunk("Content", "/path/file.xml", "h1", 0)
        ));

        List<DocumentChunk> results = vectorStoreService.search(null, 10, 0.0, null, 1.5);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldHandleNullChunksList() {
        vectorStoreService.addChunks(null);
        assertEquals(0, vectorStoreService.size());
    }

    @Test
    void shouldHandleEmptyChunksList() {
        vectorStoreService.addChunks(List.of());
        assertEquals(0, vectorStoreService.size());
    }

    @Test
    void shouldReturnResultsOrderedByDescendingScore() {
        List<DocumentChunk> chunks = List.of(
                createChunk("Spring Integration gateway handles HTTP", "/path/gateway.xml", "h1", 0),
                createChunk("Spring Integration router for messages", "/path/router.xml", "h2", 0),
                createChunk("Database connection configuration", "/path/db.xml", "h3", 0)
        );

        vectorStoreService.addChunks(chunks);

        List<DocumentChunk> results = vectorStoreService.search(
                "Spring Integration gateway HTTP", 10, 0.0, null, 1.5);

        // Verify descending order
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).getScore() >= results.get(i + 1).getScore(),
                    "Results should be ordered by descending score");
        }
    }

    @Test
    void shouldClampMaxResultsToValidRange() {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            chunks.add(createChunk("Spring Integration content " + i, "/path/file" + i + ".xml", "hash" + i, 0));
        }
        vectorStoreService.addChunks(chunks);

        // maxResults = 0 should be clamped to 1
        List<DocumentChunk> results = vectorStoreService.search("Spring Integration", 0, 0.0, null, 1.5);
        assertTrue(results.size() <= 1);

        // maxResults = 100 should be clamped to 50
        results = vectorStoreService.search("Spring Integration", 100, 0.0, null, 1.5);
        assertTrue(results.size() <= 50);
    }

    @Test
    void shouldUseConvenienceSearchMethod() {
        List<DocumentChunk> chunks = List.of(
                createChunk("Spring Integration gateway handles HTTP requests", "/path/gateway.xml", "h1", 0)
        );

        vectorStoreService.addChunks(chunks);

        // Use the convenience method with defaults
        List<DocumentChunk> results = vectorStoreService.search("Spring Integration gateway", null);
        assertFalse(results.isEmpty());
    }

    private DocumentChunk createChunk(String content, String filePath, String contentHash, int chunkIndex) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String fileType = fileName.substring(fileName.lastIndexOf('.') + 1);
        return DocumentChunk.builder()
                .content(content)
                .filePath(filePath)
                .fileName(fileName)
                .fileType(fileType)
                .integrationName("testIntegration")
                .boundType("inbound")
                .chunkIndex(chunkIndex)
                .contentHash(contentHash)
                .concepts(List.of("Gateway"))
                .build();
    }
}
