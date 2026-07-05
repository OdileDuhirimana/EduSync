package com.edusync.assessment.api.dto;

import com.edusync.assessment.domain.AssessmentSession;

import java.time.Instant;

public record AssessmentSessionResponseDto(
        String assessmentId,
        String userId,
        String token,
        int timeLimitMinutes,
        Instant startedAt
) {
    public static AssessmentSessionResponseDto from(AssessmentSession session) {
        return new AssessmentSessionResponseDto(
                session.getAssessmentId(),
                session.getUserId(),
                session.getToken(),
                session.getTimeLimitMinutes(),
                session.getStartedAt());
    }
}
