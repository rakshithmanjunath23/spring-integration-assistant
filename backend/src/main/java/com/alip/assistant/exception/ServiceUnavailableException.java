package com.alip.assistant.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String serviceName) {
        super("Service unavailable: " + serviceName);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
