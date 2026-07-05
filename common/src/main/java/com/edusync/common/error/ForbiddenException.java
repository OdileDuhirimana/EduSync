package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when an authenticated caller lacks permission for the action. Maps to HTTP 403. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", HttpStatus.FORBIDDEN, message);
    }
}
