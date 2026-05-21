package com.alip.assistant.util;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility for estimating token counts in text.
 * Uses the chars/4 approximation (GPT-style heuristic) for token estimation.
 * This is sufficient for prompt budget management without requiring an external tokenizer.
 */
@Component
public class TokenCounter {

    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Estimate the token count for a text string.
     * Uses the approximation: tokens ≈ characters / 4.
     *
     * @param text the input text
     * @return estimated token count, or 0 for null/empty text
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Estimate the total token count for multiple texts.
     *
     * @param texts list of text strings
     * @return sum of estimated token counts for all texts
     */
    public int estimateTokens(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return 0;
        }
        return texts.stream()
                .mapToInt(this::estimateTokens)
                .sum();
    }

    /**
     * Check if a text fits within a given token budget.
     *
     * @param text         the input text
     * @param budgetTokens the maximum allowed tokens
     * @return true if the estimated token count is within budget
     */
    public boolean fitsInBudget(String text, int budgetTokens) {
        return estimateTokens(text) <= budgetTokens;
    }

    /**
     * Truncate text to fit within a token budget.
     * Attempts to truncate at the nearest sentence boundary (period, question mark,
     * exclamation mark) or newline. Falls back to word boundary if no sentence
     * boundary is found.
     *
     * @param text      the input text
     * @param maxTokens the maximum allowed tokens
     * @return the truncated text, or the original text if it already fits
     */
    public String truncateToTokenBudget(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        if (fitsInBudget(text, maxTokens)) {
            return text;
        }

        int maxChars = maxTokens * CHARS_PER_TOKEN;
        if (maxChars >= text.length()) {
            return text;
        }

        String truncated = text.substring(0, maxChars);

        // Try to find the last sentence boundary
        int lastSentenceBoundary = findLastSentenceBoundary(truncated);
        if (lastSentenceBoundary > 0) {
            return truncated.substring(0, lastSentenceBoundary + 1).stripTrailing();
        }

        // Fall back to word boundary
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            return truncated.substring(0, lastSpace).stripTrailing();
        }

        // No boundary found, return hard truncation
        return truncated;
    }

    /**
     * Find the last sentence boundary (period, question mark, exclamation mark, or newline)
     * in the given text.
     *
     * @param text the text to search
     * @return the index of the last sentence boundary, or -1 if none found
     */
    private int findLastSentenceBoundary(String text) {
        int lastBoundary = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '?' || c == '!' || c == '\n') {
                lastBoundary = i;
                break;
            }
        }
        return lastBoundary;
    }
}
