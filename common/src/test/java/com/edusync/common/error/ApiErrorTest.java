package com.edusync.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHY these tests exist: {@link ApiError} is the one JSON error shape every
 * service in the monorepo returns via {@link GlobalExceptionHandler} — the
 * portfolio evaluation's API-02 finding specifically credited this as "one
 * canonical shape" project-wide, which is only true if the shape itself is
 * verified to behave correctly (timestamp is always populated, field errors
 * default to an empty, non-null list rather than null).
 */
class ApiErrorTest {

    @Test
    void ofPopulatesAllFieldsAndDefaultsToNoFieldErrors() {
        ApiError error = ApiError.of("NOT_FOUND", "Course 'x' was not found", 404, "/courses/x");

        assertThat(error.code()).isEqualTo("NOT_FOUND");
        assertThat(error.message()).isEqualTo("Course 'x' was not found");
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.path()).isEqualTo("/courses/x");
        assertThat(error.timestamp()).isNotNull();
        assertThat(error.fieldErrors()).isEmpty();
    }

    @Test
    void ofFieldErrorsUsesAFixedValidationCodeAndCarriesEveryViolation() {
        List<ApiError.FieldViolation> violations = List.of(
                new ApiError.FieldViolation("title", "must not be blank"),
                new ApiError.FieldViolation("code", "must not exceed 64 characters"));

        ApiError error = ApiError.ofFieldErrors("/courses", 400, violations);

        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.fieldErrors()).containsExactlyElementsOf(violations);
    }

    @Test
    void everyTypedExceptionMapsToItsDocumentedHttpStatusAndCode() {
        assertThat(new BadRequestException("x").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new BadRequestException("x").getCode()).isEqualTo("BAD_REQUEST");

        assertThat(new ConflictException("x").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(new ConflictException("x").getCode()).isEqualTo("CONFLICT");

        assertThat(new ForbiddenException("x").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(new ForbiddenException("x").getCode()).isEqualTo("FORBIDDEN");

        assertThat(new NotFoundException("x").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new NotFoundException("x").getCode()).isEqualTo("NOT_FOUND");

        assertThat(new UnauthorizedException("x").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new UnauthorizedException("x").getCode()).isEqualTo("UNAUTHORIZED");

        assertThat(new ServiceUnavailableException("x").getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(new ServiceUnavailableException("x").getCode()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
