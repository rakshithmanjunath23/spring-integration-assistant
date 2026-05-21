package com.alip.assistant.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    private static EmbeddingService embeddingService;

    @BeforeAll
    static void setUp() {
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
        embeddingService = new EmbeddingService(model);
    }

    @Test
    void shouldReturnNullForNullInput() {
        Embedding result = embeddingService.embed(null);
        assertNull(result);
    }

    @Test
    void shouldReturnNullForBlankInput() {
        Embedding result = embeddingService.embed("   ");
        assertNull(result);
    }

    @Test
    void shouldGenerateEmbeddingForValidText() {
        Embedding result = embeddingService.embed("Spring Integration gateway configuration");
        assertNotNull(result);
        assertEquals(384, result.dimension());
    }

    @Test
    void shouldGenerateConsistentEmbeddings() {
        String text = "Spring Integration message channel";
        Embedding first = embeddingService.embed(text);
        Embedding second = embeddingService.embed(text);

        assertNotNull(first);
        assertNotNull(second);
        assertArrayEquals(first.vector(), second.vector(), 1e-6f);
    }

    @Test
    void shouldGenerateDifferentEmbeddingsForDifferentTexts() {
        Embedding e1 = embeddingService.embed("Spring Integration gateway");
        Embedding e2 = embeddingService.embed("Database connection pooling");

        assertNotNull(e1);
        assertNotNull(e2);
        assertFalse(java.util.Arrays.equals(e1.vector(), e2.vector()));
    }

    @Test
    void shouldReturnEmptyListForNullBatch() {
        List<Embedding> results = embeddingService.embedBatch(null);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForEmptyBatch() {
        List<Embedding> results = embeddingService.embedBatch(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldEmbedBatchOfTexts() {
        List<String> texts = List.of(
                "Spring Integration flow",
                "Message channel configuration",
                "Service activator bean"
        );
        List<Embedding> results = embeddingService.embedBatch(texts);

        assertEquals(3, results.size());
        for (Embedding embedding : results) {
            assertNotNull(embedding);
            assertEquals(384, embedding.dimension());
        }
    }

    @Test
    void shouldReturnCorrectDimension() {
        assertEquals(384, embeddingService.getDimension());
    }
}
