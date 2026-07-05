package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a request conflicts with existing state (e.g. duplicate email). Maps to HTTP 409. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super("CONFLICT", HttpStatus.CONFLICT, message);
    }
}
