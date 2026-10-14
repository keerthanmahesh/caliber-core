package com.caliber.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleMaxUploadSizeExceeded() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);
        ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSizeExceeded(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.valueOf(413), response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("File Too Large", response.getBody().get("error"));
        assertEquals("Maximum upload size exceeded (max 10MB)", response.getBody().get("message"));
        assertEquals(413, response.getBody().get("status"));
    }
}
