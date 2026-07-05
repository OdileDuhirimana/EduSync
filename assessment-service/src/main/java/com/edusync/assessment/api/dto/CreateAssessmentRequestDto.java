package com.edusync.assessment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * WHY @Pattern instead of accepting any string and defaulting silently: the
 * original controller did `req.type() == null ? "QUIZ" : req.type()`,
 * meaning a typo like "QUZI" would be stored verbatim as an invalid,
 * unrecognized type with no error at all. Constraining the raw string to
 * exactly the AssessmentType enum's names means an unknown value now fails
 * bean validation with a clear 400 VALIDATION_ERROR (via
 * GlobalExceptionHandler) instead of being persisted as garbage data or
 * silently coerced. `type` is intentionally still optional (@Pattern
 * treats null as valid per the Bean Validation spec, unlike @NotBlank) so
 * that omitting it still defaults to QUIZ, matching the pre-existing
 * default behavior (see AssessmentService#create).
 */
public record CreateAssessmentRequestDto(
        @NotBlank
        String courseId,

        @NotBlank
        @Size(max = 200)
        String title,

        @Pattern(regexp = "^(QUIZ|EXAM|ASSIGNMENT)$", message = "type must be one of QUIZ, EXAM, ASSIGNMENT")
        String type
) {
}
