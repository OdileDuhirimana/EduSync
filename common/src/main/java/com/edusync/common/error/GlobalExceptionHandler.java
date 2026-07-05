package com.edusync.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Single place every Spring MVC (servlet) service in EduSync converts an
 * exception into the shared {@link ApiError} JSON shape.
 *
 * WHY centralized here rather than duplicated per service (as flagged by
 * API-02/DOM-02 in the audits): every controller previously built its own
 * ad hoc `Map.of("error", ...)` response inline, with inconsistent field
 * names, inconsistent HTTP status usage (raw ints vs. HttpStatus constants),
 * and no handling at all for unexpected runtime exceptions (which would have
 * leaked a raw Spring stack-trace whitelabel error page to API clients).
 *
 * Each service module includes `common` as a dependency and Spring component
 * scanning picks this up automatically because every service's main
 * application class is in a `com.edusync.<service>` package, a child of
 * `com.edusync`, and Spring Boot's component scan defaults to the package of
 * the `@SpringBootApplication` class's ancestor — to guarantee pickup
 * regardless of package depth, each service explicitly imports this via
 * `@Import(GlobalExceptionHandler.class)` on its application class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage(), ex.getStatus().value(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.ofFieldErrors(request.getRequestURI(), HttpStatus.BAD_REQUEST.value(), violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("MALFORMED_REQUEST", "Request body could not be parsed", HttpStatus.BAD_REQUEST.value(), request.getRequestURI()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("MISSING_HEADER", ex.getMessage(), HttpStatus.BAD_REQUEST.value(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // WHY log at ERROR with the stack trace but never return it to the client:
        // returning internal exception details (SQL text, stack frames, class
        // names) to an API caller is an information-disclosure risk (defensive
        // programming / fail securely). The client only ever sees a generic code.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR.value(), request.getRequestURI()));
    }
}
