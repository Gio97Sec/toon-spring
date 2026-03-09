package com.toonformat.spring;

/**
 * Exception thrown when TOON serialization or deserialization fails.
 */
public class ToonException extends RuntimeException {

    public ToonException(String message) {
        super(message);
    }

    public ToonException(String message, Throwable cause) {
        super(message, cause);
    }
}
