package com.caliber.exception;

/**
 * Dedicated exception thrown when neither Ollama nor Gemini AI services are properly configured.
 */
public class AiConfigurationException extends RuntimeException {

    public AiConfigurationException(String message) {
        super(message);
    }

    public AiConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
