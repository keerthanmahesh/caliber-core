package com.caliber.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response payload for the AI configuration and extraction test.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiTestResponse(
        boolean success,
        String message,
        AiExtractedJob data
) {
    public static AiTestResponse success(String message, AiExtractedJob data) {
        return new AiTestResponse(true, message, data);
    }

    public static AiTestResponse failure(String message) {
        return new AiTestResponse(false, message, null);
    }
}
