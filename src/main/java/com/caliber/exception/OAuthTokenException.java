package com.caliber.exception;

/**
 * Dedicated exception thrown when OAuth2 token operations (authorization code exchange, token refresh) fail.
 */
public class OAuthTokenException extends RuntimeException {

    public OAuthTokenException(String message) {
        super(message);
    }

    public OAuthTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
