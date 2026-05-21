package com.alip.assistant.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCounterTest {

    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
    }

    // --- estimateTokens(String) ---

    @Test
    void estimateTokens_nullText_returnsZero() {
        assertThat(tokenCounter.estimateTokens((String) null)).isEqualTo(0);
    }

    @Test
    void estimateTokens_emptyText_returnsZero() {
        assertThat(tokenCounter.estimateTokens("")).isEqualTo(0);
    }

    @Test
    void estimateTokens_shortText_returnsAtLeastOne() {
        // "Hi" is 2 chars, 2/4 = 0, but minimum is 1
        assertThat(tokenCounter.estimateTokens("Hi")).isEqualTo(1);
    }

    @Test
    void estimateTokens_typicalText_returnsCharsDiv4() {
        // 20 chars / 4 = 5 tokens
        String text = "Hello World Testing!";
        assertThat(tokenCounter.estimateTokens(text)).isEqualTo(text.length() / 4);
    }

    @Test
    void estimateTokens_longerText_returnsCorrectEstimate() {
        // 100 chars / 4 = 25 tokens
        String text = "a".repeat(100);
        assertThat(tokenCounter.estimateTokens(text)).isEqualTo(25);
    }

    // --- estimateTokens(List<String>) ---

    @Test
    void estimateTokens_nullList_returnsZero() {
        assertThat(tokenCounter.estimateTokens((List<String>) null)).isEqualTo(0);
    }

    @Test
    void estimateTokens_emptyList_returnsZero() {
        assertThat(tokenCounter.estimateTokens(Collections.emptyList())).isEqualTo(0);
    }

    @Test
    void estimateTokens_multipleTexts_returnsSumOfEstimates() {
        List<String> texts = Arrays.asList(
                "a".repeat(40),  // 10 tokens
                "b".repeat(80)   // 20 tokens
        );
        assertThat(tokenCounter.estimateTokens(texts)).isEqualTo(30);
    }

    @Test
    void estimateTokens_listWithNullAndEmpty_handlesGracefully() {
        List<String> texts = Arrays.asList(null, "", "a".repeat(20));
        assertThat(tokenCounter.estimateTokens(texts)).isEqualTo(5);
    }

    // --- fitsInBudget ---

    @Test
    void fitsInBudget_textWithinBudget_returnsTrue() {
        String text = "a".repeat(40); // 10 tokens
        assertThat(tokenCounter.fitsInBudget(text, 10)).isTrue();
    }

    @Test
    void fitsInBudget_textExceedsBudget_returnsFalse() {
        String text = "a".repeat(40); // 10 tokens
        assertThat(tokenCounter.fitsInBudget(text, 9)).isFalse();
    }

    @Test
    void fitsInBudget_nullText_returnsTrue() {
        assertThat(tokenCounter.fitsInBudget(null, 100)).isTrue();
    }

    @Test
    void fitsInBudget_exactBudget_returnsTrue() {
        String text = "a".repeat(40); // 10 tokens
        assertThat(tokenCounter.fitsInBudget(text, 10)).isTrue();
    }

    // --- truncateToTokenBudget ---

    @Test
    void truncateToTokenBudget_nullText_returnsEmpty() {
        assertThat(tokenCounter.truncateToTokenBudget(null, 10)).isEqualTo("");
    }

    @Test
    void truncateToTokenBudget_emptyText_returnsEmpty() {
        assertThat(tokenCounter.truncateToTokenBudget("", 10)).isEqualTo("");
    }

    @Test
    void truncateToTokenBudget_textFitsInBudget_returnsOriginal() {
        String text = "Hello world.";
        assertThat(tokenCounter.truncateToTokenBudget(text, 100)).isEqualTo(text);
    }

    @Test
    void truncateToTokenBudget_truncatesAtSentenceBoundary() {
        // Budget: 5 tokens = 20 chars max
        // Text has a period at position 11
        String text = "First sent. Second sentence is longer than budget.";
        String result = tokenCounter.truncateToTokenBudget(text, 5);
        assertThat(result).isEqualTo("First sent.");
    }

    @Test
    void truncateToTokenBudget_truncatesAtQuestionMark() {
        String text = "Is this ok? This part should be cut off because it exceeds the budget.";
        String result = tokenCounter.truncateToTokenBudget(text, 5);
        assertThat(result).isEqualTo("Is this ok?");
    }

    @Test
    void truncateToTokenBudget_truncatesAtNewline() {
        String text = "First line\nSecond line that is much longer and exceeds the token budget.";
        String result = tokenCounter.truncateToTokenBudget(text, 5);
        // Truncates at newline boundary, stripTrailing removes the trailing newline
        assertThat(result).isEqualTo("First line");
    }

    @Test
    void truncateToTokenBudget_fallsBackToWordBoundary() {
        // No sentence boundary in the truncated portion
        String text = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10";
        String result = tokenCounter.truncateToTokenBudget(text, 5);
        // 5 tokens = 20 chars, should truncate at a space
        assertThat(result).doesNotEndWith(" ");
        assertThat(result.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void truncateToTokenBudget_noWordBoundary_hardTruncates() {
        // A single long word with no spaces or sentence boundaries
        String text = "a".repeat(100);
        String result = tokenCounter.truncateToTokenBudget(text, 5);
        // 5 tokens = 20 chars
        assertThat(result).hasSize(20);
    }
}
