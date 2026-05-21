package com.alip.assistant.rag;

import com.alip.assistant.config.AppProperties;
import com.alip.assistant.util.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Content-type-aware chunking service using a strategy pattern.
 * Dispatches to the appropriate chunker based on file extension:
 * - Java: split by class/method boundaries
 * - XML: split by top-level Spring Integration elements
 * - YAML/YML: split by top-level keys
 * - Properties: group by shared prefix
 * - TXT/MD: split on blank-line-separated sections
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final AppProperties appProperties;
    private final TokenCounter tokenCounter;

    /** Sentence boundary pattern: period, question mark, or exclamation followed by whitespace or end */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.?!](?=\\s|$)");

    /** Fallback chunk size in tokens when a file cannot be parsed */
    private static final int FALLBACK_CHUNK_SIZE = 800;

    /**
     * Chunk a file's content based on its file type.
     *
     * @param content   the raw file content
     * @param fileName  the file name
     * @param filePath  the absolute file path
     * @param fileType  the file extension (java, xml, yaml, properties, etc.)
     * @param module    the module/integration context
     * @return list of DocumentChunks
     */
    public List<DocumentChunk> chunkFile(String content, String fileName, String filePath,
                                          String fileType, String module) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<DocumentChunk> chunks;
        try {
            String type = fileType != null ? fileType.toLowerCase() : "";
            switch (type) {
                case "java":
                    chunks = chunkJava(content, fileName, filePath, module);
                    break;
                case "xml":
                    chunks = chunkXml(content, fileName, filePath, module);
                    break;
                case "yaml":
                case "yml":
                    chunks = chunkYaml(content, fileName, filePath, module);
                    break;
                case "properties":
                    chunks = chunkProperties(content, fileName, filePath, module);
                    break;
                case "txt":
                case "md":
                case "text":
                case "markdown":
                    chunks = chunkText(content, fileName, filePath, module);
                    break;
                default:
                    chunks = chunkText(content, fileName, filePath, module);
                    break;
            }
        } catch (Exception e) {
            log.warn("Failed to parse {} with {} chunker, falling back to fixed-size segments: {}",
                    filePath, fileType, e.getMessage());
            chunks = createFallbackChunks(content, fileName, filePath, module);
        }

        // Set fileType on all chunks
        String resolvedType = fileType != null ? fileType.toLowerCase() : "txt";
        for (DocumentChunk chunk : chunks) {
            chunk.setFileType(resolvedType);
        }

        // Apply overflow splitting to any chunks that exceed the configured max token size
        int maxTokens = appProperties.getRag().getChunkSize();
        int overlapTokens = appProperties.getRag().getOverlapTokens();
        chunks = applyOverflowSplitting(chunks, maxTokens, overlapTokens);

        return chunks;
    }

    // ========================================================================
    // Java Chunker
    // ========================================================================

    /**
     * Split Java files by class/method boundaries.
     * - Groups class-level declarations (package, imports, fields, annotations) into a separate chunk
     * - Keeps each method signature + body together in one chunk
     */
    private List<DocumentChunk> chunkJava(String content, String fileName, String filePath, String module) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        // Pattern to detect method declarations
        Pattern methodPattern = Pattern.compile(
                "^\\s*(public|private|protected|default)\\s+.*\\(.*\\)\\s*(throws\\s+[\\w,\\s]+)?\\s*\\{?\\s*$"
        );
        // Pattern to detect class/interface/enum declarations
        Pattern classPattern = Pattern.compile(
                "^\\s*(public|private|protected)?\\s*(abstract|static|final)?\\s*(class|interface|enum|record)\\s+\\w+"
        );
        // Pattern to detect annotations that precede methods
        Pattern annotationPattern = Pattern.compile("^\\s*@\\w+");

        // First pass: identify method start lines
        List<Integer> methodStartLines = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (methodPattern.matcher(lines[i]).find()) {
                // Look back for annotations preceding this method
                int start = i;
                while (start > 0 && annotationPattern.matcher(lines[start - 1]).find()) {
                    start--;
                }
                methodStartLines.add(start);
            }
        }

        if (methodStartLines.isEmpty()) {
            // No methods found - treat entire file as one chunk
            chunks.add(buildChunk(content, fileName, filePath, module, 0, 1, lines.length));
            return chunks;
        }

        int chunkIdx = 0;

        // Class-level declarations: everything before the first method
        int firstMethodLine = methodStartLines.get(0);
        if (firstMethodLine > 0) {
            StringBuilder classDecl = new StringBuilder();
            for (int i = 0; i < firstMethodLine; i++) {
                classDecl.append(lines[i]).append("\n");
            }
            String classDeclContent = classDecl.toString().trim();
            if (!classDeclContent.isEmpty()) {
                chunks.add(buildChunk(classDeclContent, fileName, filePath, module, chunkIdx++, 1, firstMethodLine));
            }
        }

        // Each method: from its start line to the line before the next method (or end of file)
        for (int m = 0; m < methodStartLines.size(); m++) {
            int startLine = methodStartLines.get(m);
            int endLine = (m + 1 < methodStartLines.size()) ? methodStartLines.get(m + 1) : lines.length;

            // Find the actual end of the method by tracking braces
            int braceCount = 0;
            boolean braceStarted = false;
            int actualEnd = endLine;
            for (int i = startLine; i < endLine; i++) {
                for (char c : lines[i].toCharArray()) {
                    if (c == '{') {
                        braceCount++;
                        braceStarted = true;
                    } else if (c == '}') {
                        braceCount--;
                    }
                }
                if (braceStarted && braceCount == 0) {
                    actualEnd = i + 1;
                    break;
                }
            }

            StringBuilder methodContent = new StringBuilder();
            for (int i = startLine; i < actualEnd; i++) {
                methodContent.append(lines[i]).append("\n");
            }
            String methodStr = methodContent.toString().trim();
            if (!methodStr.isEmpty()) {
                chunks.add(buildChunk(methodStr, fileName, filePath, module, chunkIdx++,
                        startLine + 1, actualEnd));
            }

            // If there's content between end of this method and start of next method
            // (e.g., field declarations between methods), include it
            int nextStart = (m + 1 < methodStartLines.size()) ? methodStartLines.get(m + 1) : lines.length;
            if (actualEnd < nextStart) {
                StringBuilder between = new StringBuilder();
                for (int i = actualEnd; i < nextStart; i++) {
                    between.append(lines[i]).append("\n");
                }
                String betweenStr = between.toString().trim();
                if (!betweenStr.isEmpty() && betweenStr.length() > 5) {
                    // Append to previous chunk rather than creating a tiny chunk
                    if (!chunks.isEmpty()) {
                        DocumentChunk lastChunk = chunks.get(chunks.size() - 1);
                        lastChunk.setContent(lastChunk.getContent() + "\n\n" + betweenStr);
                        lastChunk.setEndLine(nextStart);
                    }
                }
            }
        }

        return chunks;
    }

    // ========================================================================
    // XML Chunker
    // ========================================================================

    /**
     * Split XML files by top-level Spring Integration elements.
     * Preserves complete opening and closing tags.
     * Recognized elements: <bean>, <int:channel>, <int:gateway>, <int:router>,
     * <int:service-activator>, <int-jms:*>, <int-http:*>, <int-ws:*>
     */
    private List<DocumentChunk> chunkXml(String content, String fileName, String filePath, String module) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Pattern to match top-level Spring Integration elements
        Pattern elementStartPattern = Pattern.compile(
                "^\\s*<(bean|int:channel|int:gateway|int:router|int:service-activator|" +
                        "int:chain|int:transformer|int:filter|int:splitter|int:aggregator|" +
                        "int:bridge|int:outbound-channel-adapter|int:inbound-channel-adapter|" +
                        "int-jms:[\\w-]+|int-http:[\\w-]+|int-ws:[\\w-]+|" +
                        "int-kafka:[\\w-]+|int-sftp:[\\w-]+|int-file:[\\w-]+)" +
                        "(\\s|>|/)"
        );

        String[] lines = content.split("\n");
        List<int[]> elementRanges = new ArrayList<>();
        StringBuilder preamble = new StringBuilder();
        int preambleEnd = -1;

        // Find all top-level element boundaries
        int i = 0;
        while (i < lines.length) {
            Matcher matcher = elementStartPattern.matcher(lines[i]);
            if (matcher.find()) {
                if (preambleEnd == -1) {
                    preambleEnd = i;
                }
                int startLine = i;

                // Check if self-closing
                if (lines[i].trim().endsWith("/>")) {
                    elementRanges.add(new int[]{startLine, i + 1});
                    i++;
                    continue;
                }

                // Find the closing tag
                String elementName = matcher.group(1);
                // Handle namespace prefix in closing tag
                String closingTag = "</" + elementName;
                int braceDepth = 0;
                boolean foundClose = false;

                for (int j = i; j < lines.length; j++) {
                    // Count angle brackets for nested elements
                    if (lines[j].contains(closingTag + ">") || lines[j].contains(closingTag + " ")) {
                        elementRanges.add(new int[]{startLine, j + 1});
                        i = j + 1;
                        foundClose = true;
                        break;
                    }
                    // Also check for self-closing on multi-line
                    if (j > i && lines[j].trim().endsWith("/>") && !lines[j].contains("<")) {
                        // This might be the end of the opening tag (self-closing multi-line)
                        // Only if no nested elements were opened
                        if (braceDepth == 0) {
                            elementRanges.add(new int[]{startLine, j + 1});
                            i = j + 1;
                            foundClose = true;
                            break;
                        }
                    }
                }
                if (!foundClose) {
                    // Couldn't find closing tag, take until next element or end
                    elementRanges.add(new int[]{startLine, lines.length});
                    i = lines.length;
                }
            } else {
                if (preambleEnd == -1) {
                    preamble.append(lines[i]).append("\n");
                }
                i++;
            }
        }

        int chunkIdx = 0;

        // Preamble chunk (XML declaration, namespace declarations, root element opening)
        String preambleStr = preamble.toString().trim();
        if (!preambleStr.isEmpty()) {
            chunks.add(buildChunk(preambleStr, fileName, filePath, module, chunkIdx++, 1,
                    preambleEnd > 0 ? preambleEnd : 1));
        }

        // Each Spring Integration element as a chunk
        for (int[] range : elementRanges) {
            StringBuilder elementContent = new StringBuilder();
            for (int j = range[0]; j < range[1]; j++) {
                elementContent.append(lines[j]).append("\n");
            }
            String elementStr = elementContent.toString().trim();
            if (!elementStr.isEmpty()) {
                chunks.add(buildChunk(elementStr, fileName, filePath, module, chunkIdx++,
                        range[0] + 1, range[1]));
            }
        }

        // If no elements were found, fall back to treating entire content as one chunk
        if (elementRanges.isEmpty() && chunks.isEmpty()) {
            chunks.add(buildChunk(content.trim(), fileName, filePath, module, 0, 1, lines.length));
        }

        return chunks;
    }

    // ========================================================================
    // YAML Chunker
    // ========================================================================

    /**
     * Split YAML files by top-level keys.
     * A top-level key is a line that starts without indentation followed by a colon.
     * All nested content under a top-level key stays in the same chunk.
     */
    private List<DocumentChunk> chunkYaml(String content, String fileName, String filePath, String module) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        // Pattern for top-level keys: line starts with non-whitespace, contains a colon
        Pattern topLevelKeyPattern = Pattern.compile("^[^\\s#].*:");

        List<int[]> sectionRanges = new ArrayList<>();
        int currentStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Skip empty lines and comments at the top
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                if (currentStart == -1) continue;
                // Comments/blanks within a section are part of that section
                continue;
            }

            if (topLevelKeyPattern.matcher(line).find()) {
                if (currentStart >= 0) {
                    sectionRanges.add(new int[]{currentStart, i});
                }
                currentStart = i;
            }
        }
        // Close the last section
        if (currentStart >= 0) {
            sectionRanges.add(new int[]{currentStart, lines.length});
        }

        int chunkIdx = 0;
        for (int[] range : sectionRanges) {
            StringBuilder section = new StringBuilder();
            for (int j = range[0]; j < range[1]; j++) {
                section.append(lines[j]).append("\n");
            }
            String sectionStr = section.toString().trim();
            if (!sectionStr.isEmpty()) {
                chunks.add(buildChunk(sectionStr, fileName, filePath, module, chunkIdx++,
                        range[0] + 1, range[1]));
            }
        }

        if (chunks.isEmpty()) {
            chunks.add(buildChunk(content.trim(), fileName, filePath, module, 0, 1, lines.length));
        }

        return chunks;
    }

    // ========================================================================
    // Properties Chunker
    // ========================================================================

    /**
     * Group properties by shared prefix.
     * The prefix is the portion before the last dot-separated segment.
     * For example: "com.accenture.alip.server.ix.holdingInquiry.timeout"
     * has prefix "com.accenture.alip.server.ix.holdingInquiry"
     */
    private List<DocumentChunk> chunkProperties(String content, String fileName, String filePath, String module) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        // Group lines by their key prefix
        Map<String, List<String>> prefixGroups = new LinkedHashMap<>();
        List<String> currentComments = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            // Collect comments and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                currentComments.add(line);
                continue;
            }

            // Extract the key (before = or :)
            String key = extractPropertyKey(trimmed);
            String prefix = extractPrefix(key);

            prefixGroups.computeIfAbsent(prefix, k -> new ArrayList<>());

            // Attach preceding comments to this group
            if (!currentComments.isEmpty()) {
                prefixGroups.get(prefix).addAll(currentComments);
                currentComments.clear();
            }
            prefixGroups.get(prefix).add(line);
        }

        // Trailing comments go to the last group or become their own chunk
        if (!currentComments.isEmpty() && !prefixGroups.isEmpty()) {
            String lastKey = null;
            for (String key : prefixGroups.keySet()) {
                lastKey = key;
            }
            if (lastKey != null) {
                prefixGroups.get(lastKey).addAll(currentComments);
            }
        }

        int chunkIdx = 0;
        int lineOffset = 1;
        for (Map.Entry<String, List<String>> entry : prefixGroups.entrySet()) {
            String chunkContent = String.join("\n", entry.getValue()).trim();
            if (!chunkContent.isEmpty()) {
                int chunkLines = entry.getValue().size();
                chunks.add(buildChunk(chunkContent, fileName, filePath, module, chunkIdx++,
                        lineOffset, lineOffset + chunkLines - 1));
                lineOffset += chunkLines;
            }
        }

        if (chunks.isEmpty()) {
            chunks.add(buildChunk(content.trim(), fileName, filePath, module, 0, 1, lines.length));
        }

        return chunks;
    }

    /**
     * Extract the property key from a line (portion before = or :).
     */
    private String extractPropertyKey(String line) {
        int eqIdx = line.indexOf('=');
        int colonIdx = line.indexOf(':');
        int splitIdx;
        if (eqIdx >= 0 && colonIdx >= 0) {
            splitIdx = Math.min(eqIdx, colonIdx);
        } else if (eqIdx >= 0) {
            splitIdx = eqIdx;
        } else if (colonIdx >= 0) {
            splitIdx = colonIdx;
        } else {
            return line.trim();
        }
        return line.substring(0, splitIdx).trim();
    }

    /**
     * Extract the prefix from a property key (portion before the last dot).
     */
    private String extractPrefix(String key) {
        int lastDot = key.lastIndexOf('.');
        if (lastDot > 0) {
            return key.substring(0, lastDot);
        }
        return key;
    }

    // ========================================================================
    // Text/Markdown Chunker
    // ========================================================================

    /**
     * Split text/markdown files on blank-line-separated sections.
     * Each paragraph or heading block becomes its own chunk.
     */
    private List<DocumentChunk> chunkText(String content, String fileName, String filePath, String module) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Split on one or more blank lines (lines containing only whitespace)
        String[] sections = content.split("\\n\\s*\\n");

        int chunkIdx = 0;
        int lineOffset = 1;
        for (String section : sections) {
            String trimmed = section.trim();
            if (!trimmed.isEmpty()) {
                int sectionLines = trimmed.split("\n").length;
                chunks.add(buildChunk(trimmed, fileName, filePath, module, chunkIdx++,
                        lineOffset, lineOffset + sectionLines - 1));
                lineOffset += sectionLines + 1; // +1 for the blank line separator
            } else {
                lineOffset++;
            }
        }

        if (chunks.isEmpty() && !content.trim().isEmpty()) {
            chunks.add(buildChunk(content.trim(), fileName, filePath, module, 0, 1,
                    content.split("\n").length));
        }

        return chunks;
    }

    // ========================================================================
    // Overflow Splitting and Metadata
    // ========================================================================

    /**
     * Split any chunks that exceed maxTokens into sub-chunks at sentence boundaries
     * or line breaks, maintaining configurable overlap between adjacent sub-chunks.
     *
     * @param chunks       the input chunks to process
     * @param maxTokens    maximum tokens per chunk (default 1000)
     * @param overlapTokens number of overlap tokens between adjacent sub-chunks (default 100)
     * @return list of chunks where all chunks are within the token limit
     */
    public List<DocumentChunk> applyOverflowSplitting(List<DocumentChunk> chunks, int maxTokens, int overlapTokens) {
        List<DocumentChunk> result = new ArrayList<>();
        int globalIndex = 0;

        for (DocumentChunk chunk : chunks) {
            int tokenCount = tokenCounter.estimateTokens(chunk.getContent());

            if (tokenCount <= maxTokens) {
                chunk.setChunkIndex(globalIndex++);
                result.add(chunk);
            } else {
                // Split this chunk into sub-chunks
                List<String> subContents = splitAtSentenceBoundary(chunk.getContent(), maxTokens, overlapTokens);
                for (String subContent : subContents) {
                    DocumentChunk subChunk = DocumentChunk.builder()
                            .id(UUID.randomUUID().toString())
                            .content(subContent)
                            .fileName(chunk.getFileName())
                            .filePath(chunk.getFilePath())
                            .fileType(chunk.getFileType())
                            .module(chunk.getModule())
                            .chunkIndex(globalIndex++)
                            .integrationName(chunk.getIntegrationName())
                            .boundType(chunk.getBoundType())
                            .concepts(chunk.getConcepts() != null ? new ArrayList<>(chunk.getConcepts()) : new ArrayList<>())
                            .startLine(chunk.getStartLine())
                            .endLine(chunk.getEndLine())
                            .build();
                    result.add(subChunk);
                }
            }
        }

        return result;
    }

    /**
     * Attach metadata to each chunk: integration name, bound type, concepts,
     * and ensure chunk index is zero-based sequential.
     *
     * @param chunks          the chunks to annotate
     * @param integrationName the integration name to set
     * @param boundType       the bound type (inbound, outbound, datamart, common)
     * @param concepts        the detected Spring Integration concepts
     */
    public void attachMetadata(List<DocumentChunk> chunks, String integrationName,
                               String boundType, List<String> concepts) {
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            chunk.setChunkIndex(i);
            chunk.setIntegrationName(integrationName);
            chunk.setBoundType(boundType);
            chunk.setConcepts(concepts != null ? new ArrayList<>(concepts) : new ArrayList<>());
        }
    }

    /**
     * Create fallback chunks using fixed-size 800-token segments with 100-token overlap.
     * Used when a file cannot be parsed by its content-type-specific chunker.
     *
     * @param content  the raw file content
     * @param fileName the file name
     * @param filePath the absolute file path
     * @param module   the module/integration context
     * @return list of fixed-size DocumentChunks
     */
    private List<DocumentChunk> createFallbackChunks(String content, String fileName,
                                                      String filePath, String module) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int overlapTokens = appProperties.getRag().getOverlapTokens();
        int charsPerToken = 4; // same approximation as TokenCounter

        int maxChars = FALLBACK_CHUNK_SIZE * charsPerToken;
        int overlapChars = overlapTokens * charsPerToken;
        int stepChars = maxChars - overlapChars;

        if (stepChars <= 0) {
            stepChars = maxChars;
        }

        int chunkIdx = 0;
        int pos = 0;
        String[] lines = content.split("\n");
        int totalLines = lines.length;

        while (pos < content.length()) {
            int end = Math.min(pos + maxChars, content.length());
            String segment = content.substring(pos, end);

            // Estimate line numbers for this segment
            int startLine = estimateLineNumber(content, pos, totalLines);
            int endLine = estimateLineNumber(content, end, totalLines);

            chunks.add(buildChunk(segment.trim(), fileName, filePath, module, chunkIdx++, startLine, endLine));

            if (end >= content.length()) {
                break;
            }
            pos += stepChars;
        }

        if (chunks.isEmpty() && !content.trim().isEmpty()) {
            chunks.add(buildChunk(content.trim(), fileName, filePath, module, 0, 1, totalLines));
        }

        return chunks;
    }

    /**
     * Split content at sentence boundaries or line breaks to fit within maxTokens,
     * maintaining overlap between adjacent sub-chunks.
     */
    private List<String> splitAtSentenceBoundary(String content, int maxTokens, int overlapTokens) {
        List<String> subChunks = new ArrayList<>();
        int charsPerToken = 4;
        int maxChars = maxTokens * charsPerToken;
        int overlapChars = overlapTokens * charsPerToken;

        if (content.length() <= maxChars) {
            subChunks.add(content);
            return subChunks;
        }

        int pos = 0;
        while (pos < content.length()) {
            int end = Math.min(pos + maxChars, content.length());

            if (end < content.length()) {
                // Try to find a sentence boundary (., ?, !) or line break near the end
                int splitPoint = findBestSplitPoint(content, pos, end);
                if (splitPoint > pos) {
                    end = splitPoint;
                }
            }

            String segment = content.substring(pos, end).trim();
            if (!segment.isEmpty()) {
                subChunks.add(segment);
            }

            if (end >= content.length()) {
                break;
            }

            // Move forward, accounting for overlap
            int advance = end - pos - overlapChars;
            if (advance <= 0) {
                // Prevent infinite loop: advance at least by half the max
                advance = maxChars / 2;
            }
            pos = pos + advance;
        }

        return subChunks;
    }

    /**
     * Find the best split point near the end of the allowed range.
     * Looks for sentence boundaries (., ?, !) or line breaks, searching backwards
     * from the end position within the last 25% of the segment.
     */
    private int findBestSplitPoint(String content, int start, int end) {
        // Search backwards from end within the last 25% of the segment
        int searchStart = start + (end - start) * 3 / 4;

        int bestSplit = -1;

        for (int i = end - 1; i >= searchStart; i--) {
            char c = content.charAt(i);
            if (c == '\n') {
                // Line break is a good split point (include the newline)
                return i + 1;
            }
            if ((c == '.' || c == '?' || c == '!') && bestSplit == -1) {
                // Sentence boundary - check it's followed by whitespace or end
                if (i + 1 >= content.length() || Character.isWhitespace(content.charAt(i + 1))) {
                    bestSplit = i + 1;
                }
            }
        }

        if (bestSplit > 0) {
            return bestSplit;
        }

        // No sentence boundary found, try to split at a word boundary
        for (int i = end - 1; i >= searchStart; i--) {
            if (Character.isWhitespace(content.charAt(i))) {
                return i + 1;
            }
        }

        // No good boundary found, use the hard limit
        return end;
    }

    /**
     * Estimate the line number for a character position in the content.
     */
    private int estimateLineNumber(String content, int charPos, int totalLines) {
        if (charPos <= 0) return 1;
        if (charPos >= content.length()) return totalLines;

        int lineCount = 1;
        for (int i = 0; i < charPos && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineCount++;
            }
        }
        return lineCount;
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Build a DocumentChunk with standard fields populated.
     */
    private DocumentChunk buildChunk(String content, String fileName, String filePath,
                                      String module, int chunkIndex, int startLine, int endLine) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content)
                .fileName(fileName)
                .filePath(filePath)
                .module(module)
                .chunkIndex(chunkIndex)
                .startLine(startLine)
                .endLine(endLine)
                .concepts(new ArrayList<>())
                .build();
    }
}
