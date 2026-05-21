package com.alip.assistant.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreTest {

    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new VectorStore();
    }

    @Test
    void shouldAddAndRetrieveChunks() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        DocumentChunk chunk = DocumentChunk.builder()
                .id(UUID.randomUUID().toString())
                .content("Test content about Spring Integration")
                .fileName("TestFile.java")
                .filePath("/test/TestFile.java")
                .fileType("java")
                .module("test")
                .chunkIndex(0)
                .embedding(embedding)
                .build();

        vectorStore.addChunk(chunk);
        assertEquals(1, vectorStore.size());
    }

    @Test
    void shouldSearchBySimilarity() {
        float[] embedding1 = new float[]{1.0f, 0.0f, 0.0f, 0.0f};
        float[] embedding2 = new float[]{0.0f, 1.0f, 0.0f, 0.0f};

        vectorStore.addChunk(DocumentChunk.builder()
                .id("1").content("Spring Integration flow")
                .fileName("Flow.java").filePath("/Flow.java")
                .fileType("java").module("test").chunkIndex(0)
                .embedding(embedding1).build());

        vectorStore.addChunk(DocumentChunk.builder()
                .id("2").content("Database configuration")
                .fileName("DB.xml").filePath("/DB.xml")
                .fileType("xml").module("test").chunkIndex(0)
                .embedding(embedding2).build());

        // Query similar to first chunk
        float[] query = new float[]{0.9f, 0.1f, 0.0f, 0.0f};
        List<DocumentChunk> results = vectorStore.search(query, 1, 0.0);

        assertFalse(results.isEmpty());
        assertEquals("1", results.get(0).getId());
    }

    @Test
    void shouldBoostPriorityFiles() {
        float[] embedding = new float[]{0.5f, 0.5f, 0.0f, 0.0f};

        vectorStore.addChunk(DocumentChunk.builder()
                .id("1").content("Content A")
                .fileName("A.java").filePath("/path/A.java")
                .fileType("java").module("test").chunkIndex(0)
                .embedding(embedding).build());

        vectorStore.addChunk(DocumentChunk.builder()
                .id("2").content("Content B")
                .fileName("B.java").filePath("/path/B.java")
                .fileType("java").module("test").chunkIndex(0)
                .embedding(embedding).build());

        float[] query = new float[]{0.5f, 0.5f, 0.0f, 0.0f};
        List<DocumentChunk> results = vectorStore.searchWithPriority(
                query, 2, 0.0, List.of("/path/B.java"));

        // B.java should be boosted to top
        assertEquals("2", results.get(0).getId());
    }

    @Test
    void shouldClearStore() {
        vectorStore.addChunk(DocumentChunk.builder()
                .id("1").content("test").fileName("t.java").filePath("/t.java")
                .fileType("java").module("test").chunkIndex(0)
                .embedding(new float[]{1.0f}).build());

        assertEquals(1, vectorStore.size());
        vectorStore.clear();
        assertEquals(0, vectorStore.size());
    }
}
