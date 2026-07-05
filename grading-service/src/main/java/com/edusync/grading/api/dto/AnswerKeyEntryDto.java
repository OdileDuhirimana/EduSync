package com.edusync.grading.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * A single answer-key entry (the correct answer and its point value) supplied
 * inline in an auto-grade request.
 *
 * WHY {@code points} is a primitive {@code int} with {@code @Positive} rather
 * than a nullable {@code Integer}: a missing "points" field in the JSON body
 * deserializes to {@code 0} for a primitive, which {@code @Positive} then
 * rejects at the HTTP boundary — the same effect as pairing a nullable
 * {@code Integer} with both {@code @NotNull} and {@code @Positive}, with one
 * fewer annotation. GradingService#autoGrade additionally re-checks the sum
 * of all points server-side (defense in depth, not reliance on Bean
 * Validation alone — see its Javadoc for why).
 */
public record AnswerKeyEntryDto(
        @NotBlank String questionId,
        @NotBlank String correctAnswer,
        @Positive int points
) {
}
