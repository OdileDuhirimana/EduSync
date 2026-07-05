package com.edusync.submission.api.dto;

import com.edusync.submission.domain.Submission;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SubmissionResponseDto(
        String id,
        String assessmentId,
        String userId,
        List<Map<String, Object>> answers,
        String status,
        Instant createdAt
) {
    public static SubmissionResponseDto from(Submission submission) {
        return new SubmissionResponseDto(
                submission.getId(),
                submission.getAssessmentId(),
                submission.getUserId(),
                submission.getAnswers(),
                submission.getStatus().name(),
                submission.getCreatedAt());
    }
}
