package com.alip.assistant.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSanitizerTest {

    @Nested
    @DisplayName("validateChatMessage")
    class ValidateChatMessage {

        @Test
        @DisplayName("accepts valid message and trims whitespace")
        void acceptsValidMessage() {
            String result = InputSanitizer.validateChatMessage("  Hello, world!  ");
            assertEquals("Hello, world!", result);
        }

        @Test
        @DisplayName("accepts message with tabs and newlines")
        void acceptsTabsAndNewlines() {
            String message = "Line 1\n\tLine 2\r\nLine 3";
            String result = InputSanitizer.validateChatMessage(message);
            assertEquals(message, result);
        }

        @Test
        @DisplayName("rejects null message")
        void rejectsNull() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage(null));
            assertTrue(ex.getMessage().contains("null or blank"));
        }

        @Test
        @DisplayName("rejects blank message")
        void rejectsBlank() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage("   "));
            assertTrue(ex.getMessage().contains("null or blank"));
        }

        @Test
        @DisplayName("rejects empty message")
        void rejectsEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage(""));
        }

        @Test
        @DisplayName("rejects message exceeding 4000 characters")
        void rejectsOverLength() {
            String longMessage = "a".repeat(4001);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage(longMessage));
            assertTrue(ex.getMessage().contains("maximum length"));
        }

        @Test
        @DisplayName("accepts message at exactly 4000 characters")
        void acceptsExactMaxLength() {
            String message = "a".repeat(4000);
            String result = InputSanitizer.validateChatMessage(message);
            assertEquals(4000, result.length());
        }

        @Test
        @DisplayName("rejects message with null byte")
        void rejectsNullByte() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage("hello\0world"));
            assertTrue(ex.getMessage().contains("null bytes"));
        }

        @Test
        @DisplayName("rejects message with control characters")
        void rejectsControlChars() {
            // ASCII 0x01 (SOH) is a control character
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage("hello\u0001world"));
            assertTrue(ex.getMessage().contains("control characters"));
        }

        @Test
        @DisplayName("rejects message with bell character")
        void rejectsBellChar() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage("hello\u0007world"));
        }

        @Test
        @DisplayName("rejects message with escape character")
        void rejectsEscapeChar() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateChatMessage("hello\u001Bworld"));
        }
    }

    @Nested
    @DisplayName("validateFilePath")
    class ValidateFilePath {

        @Test
        @DisplayName("accepts valid file path")
        void acceptsValidPath() {
            String result = InputSanitizer.validateFilePath("/home/user/project/src/Main.java");
            assertEquals("/home/user/project/src/Main.java", result);
        }

        @Test
        @DisplayName("accepts path with dots in filename")
        void acceptsDotsInFilename() {
            String result = InputSanitizer.validateFilePath("/home/user/file.test.xml");
            assertEquals("/home/user/file.test.xml", result);
        }

        @Test
        @DisplayName("rejects null path")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath(null));
        }

        @Test
        @DisplayName("rejects blank path")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("  "));
        }

        @Test
        @DisplayName("rejects path with semicolon")
        void rejectsSemicolon() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("/home/user; rm -rf /"));
            assertTrue(ex.getMessage().contains("disallowed characters"));
        }

        @Test
        @DisplayName("rejects path with pipe")
        void rejectsPipe() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("/home/user | cat /etc/passwd"));
        }

        @Test
        @DisplayName("rejects path with double ampersand")
        void rejectsDoubleAmpersand() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("/home/user && echo pwned"));
        }

        @Test
        @DisplayName("rejects path with backtick")
        void rejectsBacktick() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("/home/user/`whoami`/file"));
        }

        @Test
        @DisplayName("rejects path with null byte")
        void rejectsNullByte() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("/home/user\0/file"));
        }

        @Test
        @DisplayName("rejects path with control characters")
        void rejectsControlChars() {
            assertThrows(IllegalArgumentException.class,
                    () -> InputSanitizer.validateFilePath("/home/user\u0001/file"));
        }

        @Test
        @DisplayName("accepts path with single ampersand (not injection)")
        void acceptsSingleAmpersand() {
            String result = InputSanitizer.validateFilePath("/home/user/R&D/file.txt");
            assertEquals("/home/user/R&D/file.txt", result);
        }
    }

    @Nested
    @DisplayName("sanitizeForLog")
    class SanitizeForLog {

        @Test
        @DisplayName("returns input unchanged when no control characters")
        void returnsUnchanged() {
            String result = InputSanitizer.sanitizeForLog("Hello, world!");
            assertEquals("Hello, world!", result);
        }

        @Test
        @DisplayName("replaces control characters with unicode escape")
        void replacesControlChars() {
            String result = InputSanitizer.sanitizeForLog("hello\u0001world");
            assertEquals("hello\\u0001world", result);
        }

        @Test
        @DisplayName("replaces null byte with unicode escape")
        void replacesNullByte() {
            String result = InputSanitizer.sanitizeForLog("hello\u0000world");
            assertEquals("hello\\u0000world", result);
        }

        @Test
        @DisplayName("preserves tabs and newlines")
        void preservesTabsAndNewlines() {
            String result = InputSanitizer.sanitizeForLog("line1\n\tline2\r\n");
            assertEquals("line1\n\tline2\r\n", result);
        }

        @Test
        @DisplayName("truncates to 500 characters")
        void truncatesLongInput() {
            String longInput = "a".repeat(600);
            String result = InputSanitizer.sanitizeForLog(longInput);
            assertEquals(500, result.length());
        }

        @Test
        @DisplayName("does not truncate input at exactly 500 characters")
        void doesNotTruncateAtExactLimit() {
            String input = "a".repeat(500);
            String result = InputSanitizer.sanitizeForLog(input);
            assertEquals(500, result.length());
        }

        @Test
        @DisplayName("returns [null] for null input")
        void handlesNull() {
            String result = InputSanitizer.sanitizeForLog(null);
            assertEquals("[null]", result);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmpty() {
            String result = InputSanitizer.sanitizeForLog("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("replaces multiple control characters")
        void replacesMultipleControlChars() {
            String result = InputSanitizer.sanitizeForLog("\u0001\u0002\u0003");
            assertEquals("\\u0001\\u0002\\u0003", result);
        }
    }
}
