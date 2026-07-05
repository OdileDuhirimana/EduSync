package com.edusync.grading.api.dto;

import com.edusync.grading.domain.GradeRecord;
import com.edusync.grading.domain.RegradeCase;

import java.time.Instant;

/**
 * WHY {@code currentGrade} is nullable rather than always populated: it
 * reflects the submission's grade at the moment this response was built.
 * Every regrade case is created only for a submission that already has a
 * grade (see GradingService#requestRegrade), so in practice it is always
 * present for cases reachable through the API — it stays nullable in the
 * type to make that "no grade found" edge case explicit to API clients
 * rather than throwing away information silently.
 */
public record RegradeResponseDto(
        String requestId,
        String submissionId,
        String status,
        String requestedBy,
        String reason,
        Instant requestedAt,
        String decidedBy,
        String decisionNote,
        Instant decidedAt,
        Integer overrideTotal,
        GradeResponseDto currentGrade
) {
    public static RegradeResponseDto from(RegradeCase regradeCase, GradeRecord grade) {
        return new RegradeResponseDto(
                regradeCase.getId(),
                regradeCase.getSubmissionId(),
                regradeCase.getStatus().name(),
                regradeCase.getRequestedBy(),
                regradeCase.getReason(),
                regradeCase.getRequestedAt(),
                regradeCase.getDecidedBy(),
                regradeCase.getDecisionNote(),
                regradeCase.getDecidedAt(),
                regradeCase.getOverrideTotal(),
                grade == null ? null : GradeResponseDto.from(grade));
    }
}
