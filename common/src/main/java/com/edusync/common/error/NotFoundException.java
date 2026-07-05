package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
