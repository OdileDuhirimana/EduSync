package com.edusync.grading.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A single submitted answer supplied inline in an auto-grade request.
 *
 * WHY {@code response} is not {@code @NotBlank}: a blank/omitted response
 * legitimately represents "the student did not answer this question," which
 * must score 0 rather than fail request validation — see
 * GradingService#autoGrade for how a missing/blank response is treated as a
 * non-match.
 */
public record AnswerDto(
        @NotBlank String questionId,
        String response
) {
}
