package com.edusync.grading.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * Request body for {@code POST /grading/manual/{submissionId}}.
 *
 * WHY {@code breakdown} values are typed {@code Integer} rather than the
 * previous {@code Map<String, Object>}: DOM-03/DOM-04-class stringly/loosely
 * typed modeling — the original controller silently coerced non-numeric
 * values to 0 via an `instanceof Number` check, hiding a caller mistake
 * (e.g. a typo'd points value) instead of rejecting it. A typed
 * {@code Map<String, Integer>} lets Bean Validation and Jackson reject a
 * malformed body (e.g. a string points value) with a clear 400 instead of
 * silently scoring it as zero.
 */
public record ManualGradeRequestDto(
        @NotEmpty Map<String, Integer> breakdown,
        String feedback
) {
}
