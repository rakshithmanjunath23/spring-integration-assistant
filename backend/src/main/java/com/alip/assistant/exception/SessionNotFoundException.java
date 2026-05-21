package com.alip.assistant.exception;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("Chat session with id '" + sessionId + "' was not found");
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
