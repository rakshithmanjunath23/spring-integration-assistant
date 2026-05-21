package com.alip.assistant.util;

import org.springframework.stereotype.Component;

/**
 * Utility class for input sanitization and validation.
 * Prevents OS command injection, null byte attacks, and control character abuse.
 */
@Component
public class InputSanitizer {

    private static final int MAX_CHAT_MESSAGE_LENGTH = 4000;
    private static final int MAX_LOG_LENGTH = 500;

    /**
     * Validates a chat message input.
     * Rejects null/blank messages, messages exceeding 4000 characters,
     * messages containing null bytes, and messages with control characters
     * (ASCII 0-31 except tab, newline, carriage return).
     *
     * @param message the chat message to validate
     * @return the validated and trimmed message
     * @throws IllegalArgumentException if the message is invalid
     */
    public static String validateChatMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Chat message must not be null or blank");
        }

        if (message.length() > MAX_CHAT_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Chat message exceeds maximum length of " + MAX_CHAT_MESSAGE_LENGTH + " characters");
        }

        if (message.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Chat message must not contain null bytes");
        }

        if (containsControlCharacters(message)) {
            throw new IllegalArgumentException(
                    "Chat message must not contain control characters");
        }

        return message.trim();
    }

    /**
     * Validates a file path input.
     * Rejects null/blank paths, paths containing OS command injection sequences
     * (semicolons, pipes, ampersands, backticks), null bytes, and control characters.
     *
     * @param path the file path to validate
     * @return the validated path
     * @throws IllegalArgumentException if the path is invalid
     */
    public static String validateFilePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("File path must not be null or blank");
        }

        if (path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("File path must not contain null bytes");
        }

        if (containsControlCharacters(path)) {
            throw new IllegalArgumentException(
                    "File path must not contain control characters");
        }

        if (containsCommandInjection(path)) {
            throw new IllegalArgumentException(
                    "File path contains disallowed characters");
        }

        return path;
    }

    /**
     * Sanitizes input before including in log entries.
     * Replaces control characters with their unicode escape representation
     * and truncates to a maximum of 500 characters for log safety.
     *
     * @param input the input to sanitize for logging
     * @return the sanitized string, or "[null]" if input is null
     */
    public static String sanitizeForLog(String input) {
        if (input == null) {
            return "[null]";
        }

        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }

        String sanitized = sb.toString();
        if (sanitized.length() > MAX_LOG_LENGTH) {
            return sanitized.substring(0, MAX_LOG_LENGTH);
        }
        return sanitized;
    }

    /**
     * Checks if the input contains control characters (ASCII 0-31)
     * excluding tab (\t = 9), newline (\n = 10), and carriage return (\r = 13).
     */
    private static boolean containsControlCharacters(String input) {
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the input contains OS command injection sequences:
     * semicolons (;), pipes (|), double ampersands (&&), or backticks (`).
     */
    private static boolean containsCommandInjection(String input) {
        if (input.contains(";") || input.contains("|") || input.contains("&&") || input.contains("`")) {
            return true;
        }
        return false;
    }
}
