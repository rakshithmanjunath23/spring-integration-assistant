package com.alip.assistant.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VectorStore {

    private final Map<String, DocumentChunk> store = new ConcurrentHashMap<>();

    public void addChunk(DocumentChunk chunk) {
        store.put(chunk.getId(), chunk);
    }

    public void addChunks(List<DocumentChunk> chunks) {
        chunks.forEach(this::addChunk);
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    public List<DocumentChunk> search(float[] queryEmbedding, int maxResults, double threshold) {
        return store.values().stream()
                .map(chunk -> {
                    double score = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
                    return DocumentChunk.builder()
                            .id(chunk.getId())
                            .content(chunk.getContent())
                            .fileName(chunk.getFileName())
                            .filePath(chunk.getFilePath())
                            .fileType(chunk.getFileType())
                            .module(chunk.getModule())
                            .chunkIndex(chunk.getChunkIndex())
                            .embedding(chunk.getEmbedding())
                            .score(score)
                            .build();
                })
                .filter(chunk -> chunk.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(DocumentChunk::getScore).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    public List<DocumentChunk> searchWithPriority(float[] queryEmbedding, int maxResults,
                                                   double threshold, List<String> priorityFiles) {
        if (priorityFiles == null || priorityFiles.isEmpty()) {
            return search(queryEmbedding, maxResults, threshold);
        }

        Set<String> prioritySet = new HashSet<>(priorityFiles);

        return store.values().stream()
                .map(chunk -> {
                    double score = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
                    if (prioritySet.contains(chunk.getFilePath())) {
                        score = Math.min(1.0, score * 1.3);
                    }
                    return DocumentChunk.builder()
                            .id(chunk.getId())
                            .content(chunk.getContent())
                            .fileName(chunk.getFileName())
                            .filePath(chunk.getFilePath())
                            .fileType(chunk.getFileType())
                            .module(chunk.getModule())
                            .chunkIndex(chunk.getChunkIndex())
                            .embedding(chunk.getEmbedding())
                            .score(score)
                            .build();
                })
                .filter(chunk -> chunk.getScore() >= threshold)
                .sorted(Comparator.comparingDouble(DocumentChunk::getScore).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }
}
