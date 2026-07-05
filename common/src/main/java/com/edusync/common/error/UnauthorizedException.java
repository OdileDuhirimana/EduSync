package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when authentication is missing or invalid (e.g. bad credentials). Maps to HTTP 401. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, message);
    }
}
