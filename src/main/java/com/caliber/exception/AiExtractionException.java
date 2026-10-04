package com.caliber.exception;

/**
 * Dedicated exception thrown when AI model extraction (Ollama / Gemini) fails.
 */
public class AiExtractionException extends RuntimeException {

    public AiExtractionException(String message) {
        super(message);
    }

    public AiExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
