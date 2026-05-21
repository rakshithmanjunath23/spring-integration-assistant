package com.alip.assistant.exception;

/**
 * Exception thrown when a file access request is denied due to path traversal
 * or the requested path being outside the configured project root.
 */
public class FileAccessDeniedException extends RuntimeException {

    private final String requestedPath;

    public FileAccessDeniedException(String message) {
        super(message);
        this.requestedPath = null;
    }

    public FileAccessDeniedException(String message, String requestedPath) {
        super(message);
        this.requestedPath = requestedPath;
    }

    public FileAccessDeniedException(String message, String requestedPath, Throwable cause) {
        super(message, cause);
        this.requestedPath = requestedPath;
    }

    public FileAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
        this.requestedPath = null;
    }

    public String getRequestedPath() {
        return requestedPath;
    }
}
