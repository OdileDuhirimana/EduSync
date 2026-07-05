package com.edusync.grading.api.dto;

import com.edusync.grading.domain.GradeRecord;

import java.time.Instant;
import java.util.Map;

public record GradeResponseDto(
        String id,
        String submissionId,
        Map<String, Integer> breakdown,
        int total,
        String feedback,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static GradeResponseDto from(GradeRecord grade) {
        return new GradeResponseDto(
                grade.getId(),
                grade.getSubmissionId(),
                grade.getBreakdown(),
                grade.getTotal(),
                grade.getFeedback(),
                grade.getStatus().name(),
                grade.getCreatedAt(),
                grade.getUpdatedAt());
    }
}
