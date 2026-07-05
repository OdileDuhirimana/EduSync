package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for all business-rule failures that should be translated into a
 * structured {@link ApiError} response by GlobalExceptionHandler, instead of
 * each controller building its own ResponseEntity by hand.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
