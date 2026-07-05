package com.edusync.submission.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * WHY answers is `@NotEmpty` rather than left unvalidated (the original
 * controller silently coerced a null/empty `answers` field to `List.of()`
 * and stored it anyway): an empty submission carries no comparison text and
 * cannot be meaningfully checked for plagiarism, so rejecting it up front
 * with a clear 400 is more useful to a caller than silently accepting a
 * submission that will always score 0.0 similarity against everything.
 * Nested answer content is intentionally left as arbitrary
 * `List<Map<String,Object>>` — see AnswersJsonConverter's Javadoc for why
 * this is schema-less by design.
 */
public record CreateSubmissionRequestDto(
        @NotBlank
        String assessmentId,

        @NotEmpty
        List<Map<String, Object>> answers
) {
}
