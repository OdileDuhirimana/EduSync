package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/** Thrown for semantically invalid requests that pass bean validation but fail a business rule. Maps to HTTP 400. */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", HttpStatus.BAD_REQUEST, message);
    }
}
