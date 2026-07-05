package com.edusync.assessment.api.dto;

import com.edusync.assessment.domain.Assessment;

import java.time.Instant;

public record AssessmentResponseDto(
        String id,
        String courseId,
        String title,
        String type,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssessmentResponseDto from(Assessment assessment) {
        return new AssessmentResponseDto(
                assessment.getId(),
                assessment.getCourseId(),
                assessment.getTitle(),
                assessment.getType().name(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt());
    }
}
