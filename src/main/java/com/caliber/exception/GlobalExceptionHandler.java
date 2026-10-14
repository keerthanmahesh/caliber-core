package com.caliber.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        HttpStatus status = HttpStatus.valueOf(413);
        return ResponseEntity.status(status)
                .body(Map.of(
                        "error", "File Too Large",
                        "message", "Maximum upload size exceeded (max 10MB)",
                        "status", status.value()
                ));
    }
}
