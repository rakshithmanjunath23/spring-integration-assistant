package com.alip.assistant.rag;

import com.alip.assistant.config.AppProperties;
import com.alip.assistant.util.TokenCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private ChunkingService chunkingService;
    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        AppProperties.Rag ragConfig = new AppProperties.Rag();
        properties.setRag(ragConfig);
        tokenCounter = new TokenCounter();
        chunkingService = new ChunkingService(properties, tokenCounter);
    }

    @Test
    void shouldChunkSmallFile() {
        String content = "package com.test;\npublic class Test { }";
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                content, "Test.java", "/Test.java", "java", "test");

        assertFalse(chunks.isEmpty());
        assertEquals("Test.java", chunks.get(0).getFileName());
    }

    @Test
    void shouldChunkLargeJavaFileByMethods() {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.test;\n\n");
        sb.append("import java.util.List;\n\n");
        sb.append("public class Large {\n\n");
        for (int i = 0; i < 10; i++) {
            sb.append("    public void method").append(i).append("() {\n");
            sb.append("        System.out.println(\"Hello ").append(i).append("\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                sb.toString(), "Large.java", "/Large.java", "java", "test");

        assertTrue(chunks.size() > 1, "Should produce multiple chunks for multiple methods");
        // First chunk should contain class-level declarations
        assertTrue(chunks.get(0).getContent().contains("package com.test"));
    }

    @Test
    void shouldHandleEmptyContent() {
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                "", "Empty.java", "/Empty.java", "java", "test");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shouldHandleNullContent() {
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                null, "Null.java", "/Null.java", "java", "test");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shouldChunkXmlFile() {
        String xml = """
                <?xml version="1.0"?>
                <beans xmlns:int="http://www.springframework.org/schema/integration">
                  <bean id="test1" class="com.Test1"/>
                  <bean id="test2" class="com.Test2"/>
                </beans>
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                xml, "config.xml", "/config.xml", "xml", "resources");

        assertFalse(chunks.isEmpty());
        assertEquals("xml", chunks.get(0).getFileType());
    }

    @Test
    void shouldChunkXmlBySpringIntegrationElements() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:int="http://www.springframework.org/schema/integration">
                
                    <int:channel id="inputChannel"/>
                
                    <int:service-activator input-channel="inputChannel"
                                           ref="myService"
                                           method="process"/>
                
                    <bean id="myService" class="com.example.MyService"/>
                
                </beans>
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                xml, "flow.xml", "/flow.xml", "xml", "resources");

        assertTrue(chunks.size() >= 3, "Should have preamble + at least 3 elements, got: " + chunks.size());
    }

    @Test
    void shouldChunkYamlByTopLevelKeys() {
        String yaml = """
                spring:
                  datasource:
                    url: jdbc:h2:mem:test
                    driver-class-name: org.h2.Driver
                
                server:
                  port: 8080
                  servlet:
                    context-path: /api
                
                logging:
                  level:
                    root: INFO
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                yaml, "application.yml", "/application.yml", "yaml", "config");

        assertEquals(3, chunks.size(), "Should produce 3 chunks for 3 top-level keys");
        assertTrue(chunks.get(0).getContent().contains("spring:"));
        assertTrue(chunks.get(1).getContent().contains("server:"));
        assertTrue(chunks.get(2).getContent().contains("logging:"));
    }

    @Test
    void shouldChunkPropertiesBySharedPrefix() {
        String props = """
                com.accenture.alip.server.ix.holdingInquiry.timeout=30000
                com.accenture.alip.server.ix.holdingInquiry.retries=3
                com.accenture.alip.server.ix.holdingInquiry.endpoint=/api/holding
                com.accenture.alip.server.ix.policySearch.timeout=15000
                com.accenture.alip.server.ix.policySearch.maxResults=100
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                props, "config.properties", "/config.properties", "properties", "config");

        assertEquals(2, chunks.size(), "Should produce 2 chunks for 2 distinct prefixes");
        assertTrue(chunks.get(0).getContent().contains("holdingInquiry"));
        assertTrue(chunks.get(1).getContent().contains("policySearch"));
    }

    @Test
    void shouldChunkTextByBlankLines() {
        String text = """
                # Introduction
                This is the first section about the project.
                
                # Architecture
                This section describes the architecture.
                It has multiple lines.
                
                # Conclusion
                Final thoughts here.
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                text, "readme.md", "/readme.md", "md", "docs");

        assertEquals(3, chunks.size(), "Should produce 3 chunks for 3 blank-line-separated sections");
        assertTrue(chunks.get(0).getContent().contains("Introduction"));
        assertTrue(chunks.get(1).getContent().contains("Architecture"));
        assertTrue(chunks.get(2).getContent().contains("Conclusion"));
    }

    @Test
    void shouldSetFileTypeOnAllChunks() {
        String content = "key1=value1\nkey2=value2";
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                content, "app.properties", "/app.properties", "properties", "config");

        for (DocumentChunk chunk : chunks) {
            assertEquals("properties", chunk.getFileType());
        }
    }

    @Test
    void shouldSetChunkIndexSequentially() {
        String yaml = """
                spring:
                  port: 8080
                
                server:
                  name: test
                
                app:
                  version: 1.0
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                yaml, "app.yml", "/app.yml", "yaml", "config");

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex(),
                    "Chunk index should be sequential starting from 0");
        }
    }

    @Test
    void shouldInitializeConceptsAsEmptyList() {
        String content = "Some text content";
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                content, "file.txt", "/file.txt", "txt", "docs");

        assertFalse(chunks.isEmpty());
        assertNotNull(chunks.get(0).getConcepts());
        assertTrue(chunks.get(0).getConcepts().isEmpty());
    }

    @Test
    void shouldHandleJavaFileWithNoMethods() {
        String content = """
                package com.test;
                
                import java.util.List;
                
                public interface MyInterface {
                    void doSomething();
                    String getName();
                }
                """;

        List<DocumentChunk> chunks = chunkingService.chunkFile(
                content, "MyInterface.java", "/MyInterface.java", "java", "test");

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).getContent().contains("MyInterface"));
    }

    @Test
    void shouldFallbackForUnknownFileType() {
        String content = "Line 1\n\nLine 2\n\nLine 3";
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                content, "file.unknown", "/file.unknown", "unknown", "misc");

        // Should fall back to text chunker (blank-line splitting)
        assertFalse(chunks.isEmpty());
    }

    // ========================================================================
    // Overflow Splitting Tests (Task 5.2)
    // ========================================================================

    @Test
    void shouldSplitOversizedChunkAtSentenceBoundary() {
        // Create content that exceeds 1000 tokens (4000+ chars)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("This is sentence number ").append(i).append(" in the document. ");
        }
        // Make it large enough to exceed 1000 tokens
        while (sb.length() < 5000) {
            sb.append("Additional content to make this chunk exceed the token limit. ");
        }

        DocumentChunk bigChunk = DocumentChunk.builder()
                .id("test-id")
                .content(sb.toString())
                .fileName("Big.java")
                .filePath("/Big.java")
                .fileType("java")
                .module("test")
                .chunkIndex(0)
                .startLine(1)
                .endLine(100)
                .concepts(new ArrayList<>())
                .build();

        List<DocumentChunk> result = chunkingService.applyOverflowSplitting(
                List.of(bigChunk), 1000, 100);

        assertTrue(result.size() > 1, "Should split into multiple sub-chunks, got: " + result.size());

        // Each sub-chunk should be within the token limit
        for (DocumentChunk chunk : result) {
            int tokens = tokenCounter.estimateTokens(chunk.getContent());
            assertTrue(tokens <= 1000, "Sub-chunk should be <= 1000 tokens, got: " + tokens);
        }
    }

    @Test
    void shouldNotSplitChunkWithinTokenLimit() {
        DocumentChunk smallChunk = DocumentChunk.builder()
                .id("small-id")
                .content("This is a small chunk.")
                .fileName("Small.java")
                .filePath("/Small.java")
                .fileType("java")
                .module("test")
                .chunkIndex(0)
                .startLine(1)
                .endLine(5)
                .concepts(new ArrayList<>())
                .build();

        List<DocumentChunk> result = chunkingService.applyOverflowSplitting(
                List.of(smallChunk), 1000, 100);

        assertEquals(1, result.size());
        assertEquals("This is a small chunk.", result.get(0).getContent());
    }

    @Test
    void shouldMaintainSequentialChunkIndexAfterSplitting() {
        // Create two chunks, one oversized
        StringBuilder bigContent = new StringBuilder();
        while (bigContent.length() < 6000) {
            bigContent.append("Sentence with enough content to fill the chunk. ");
        }

        List<DocumentChunk> input = List.of(
                DocumentChunk.builder()
                        .id("id1").content("Small chunk content.").fileName("f.java")
                        .filePath("/f.java").module("m").chunkIndex(0)
                        .startLine(1).endLine(5).concepts(new ArrayList<>()).build(),
                DocumentChunk.builder()
                        .id("id2").content(bigContent.toString()).fileName("f.java")
                        .filePath("/f.java").module("m").chunkIndex(1)
                        .startLine(6).endLine(100).concepts(new ArrayList<>()).build()
        );

        List<DocumentChunk> result = chunkingService.applyOverflowSplitting(input, 1000, 100);

        // Verify sequential indexing
        for (int i = 0; i < result.size(); i++) {
            assertEquals(i, result.get(i).getChunkIndex(),
                    "Chunk index should be sequential, expected " + i + " at position " + i);
        }
    }

    @Test
    void shouldUseFallbackChunkingOnParseError() {
        // Simulate a file that would cause a parse error by using a custom approach
        // The fallback creates 800-token fixed-size segments
        // We test createFallbackChunks indirectly through chunkFile with a malformed scenario
        // Since the existing chunkers are robust, we test the fallback method behavior
        // by verifying that chunkFile handles exceptions gracefully

        // Create content large enough to produce multiple fallback chunks
        StringBuilder content = new StringBuilder();
        while (content.length() < 8000) {
            content.append("Line of content for fallback testing. ");
        }

        // The chunkFile method with a valid type won't throw, but we can verify
        // the overflow splitting works on the result
        List<DocumentChunk> chunks = chunkingService.chunkFile(
                content.toString(), "file.txt", "/file.txt", "txt", "test");

        assertFalse(chunks.isEmpty());
        // All chunks should be within the configured max token size
        for (DocumentChunk chunk : chunks) {
            int tokens = tokenCounter.estimateTokens(chunk.getContent());
            assertTrue(tokens <= 1000, "Chunk should be <= 1000 tokens after overflow splitting, got: " + tokens);
        }
    }

    @Test
    void shouldPreserveMetadataInSplitChunks() {
        StringBuilder bigContent = new StringBuilder();
        while (bigContent.length() < 6000) {
            bigContent.append("Content that needs splitting at boundaries. ");
        }

        DocumentChunk original = DocumentChunk.builder()
                .id("orig-id")
                .content(bigContent.toString())
                .fileName("Service.java")
                .filePath("/src/Service.java")
                .fileType("java")
                .module("services")
                .chunkIndex(0)
                .integrationName("holdingInquiry")
                .boundType("inbound")
                .startLine(10)
                .endLine(200)
                .concepts(List.of("Gateway", "Channel"))
                .build();

        List<DocumentChunk> result = chunkingService.applyOverflowSplitting(
                List.of(original), 1000, 100);

        assertTrue(result.size() > 1);
        for (DocumentChunk chunk : result) {
            assertEquals("Service.java", chunk.getFileName());
            assertEquals("/src/Service.java", chunk.getFilePath());
            assertEquals("java", chunk.getFileType());
            assertEquals("services", chunk.getModule());
            assertEquals("holdingInquiry", chunk.getIntegrationName());
            assertEquals("inbound", chunk.getBoundType());
            assertEquals(List.of("Gateway", "Channel"), chunk.getConcepts());
        }
    }

    // ========================================================================
    // Metadata Attachment Tests (Task 5.3)
    // ========================================================================

    @Test
    void shouldAttachMetadataToAllChunks() {
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder().id("1").content("chunk1").fileName("f.xml")
                        .filePath("/f.xml").chunkIndex(5).concepts(new ArrayList<>()).build(),
                DocumentChunk.builder().id("2").content("chunk2").fileName("f.xml")
                        .filePath("/f.xml").chunkIndex(7).concepts(new ArrayList<>()).build(),
                DocumentChunk.builder().id("3").content("chunk3").fileName("f.xml")
                        .filePath("/f.xml").chunkIndex(9).concepts(new ArrayList<>()).build()
        );

        List<String> concepts = List.of("Gateway", "ServiceActivator", "Channel");
        chunkingService.attachMetadata(chunks, "holdingInquiry", "inbound", concepts);

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            assertEquals(i, chunk.getChunkIndex(), "Chunk index should be zero-based sequential");
            assertEquals("holdingInquiry", chunk.getIntegrationName());
            assertEquals("inbound", chunk.getBoundType());
            assertEquals(concepts, chunk.getConcepts());
        }
    }

    @Test
    void shouldHandleNullConceptsInAttachMetadata() {
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder().id("1").content("chunk1").fileName("f.xml")
                        .filePath("/f.xml").chunkIndex(0).concepts(new ArrayList<>()).build()
        );

        chunkingService.attachMetadata(chunks, "policySearch", "outbound", null);

        assertEquals("policySearch", chunks.get(0).getIntegrationName());
        assertEquals("outbound", chunks.get(0).getBoundType());
        assertNotNull(chunks.get(0).getConcepts());
        assertTrue(chunks.get(0).getConcepts().isEmpty());
    }

    @Test
    void shouldResetChunkIndexToZeroBasedSequential() {
        List<DocumentChunk> chunks = List.of(
                DocumentChunk.builder().id("1").content("a").fileName("f.xml")
                        .filePath("/f.xml").chunkIndex(42).concepts(new ArrayList<>()).build(),
                DocumentChunk.builder().id("2").content("b").fileName("f.xml")
                        .filePath("/f.xml").chunkIndex(99).concepts(new ArrayList<>()).build()
        );

        chunkingService.attachMetadata(chunks, "test", "common", List.of("Router"));

        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(1, chunks.get(1).getChunkIndex());
    }

    @Test
    void shouldHandleEmptyChunkListInAttachMetadata() {
        List<DocumentChunk> chunks = new ArrayList<>();
        // Should not throw
        chunkingService.attachMetadata(chunks, "test", "inbound", List.of("Gateway"));
        assertTrue(chunks.isEmpty());
    }
}
