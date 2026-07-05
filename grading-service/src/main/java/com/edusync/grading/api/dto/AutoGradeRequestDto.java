package com.edusync.grading.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code POST /grading/auto/{submissionId}}.
 *
 * WHY the caller supplies both the submitted answers and the answer key
 * inline, rather than this service fetching them from submission-service /
 * assessment-service itself: grading-service has no compile-time or HTTP
 * dependency on either service in this pass (see
 * GradingService#autoGrade's Javadoc — this is a documented follow-up
 * integration opportunity, not a silently dropped requirement). The caller
 * (the gateway-routed client orchestrating the grading flow) is responsible
 * for assembling both inputs today.
 */
public record AutoGradeRequestDto(
        @NotEmpty @Valid List<AnswerDto> answers,
        @NotEmpty @Valid List<AnswerKeyEntryDto> answerKey
) {
}
