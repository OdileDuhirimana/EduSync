package com.edusync.enrollment.api.dto;

import com.edusync.enrollment.domain.Enrollment;

import java.time.Instant;

public record EnrollmentResponseDto(
        String id,
        String tenantId,
        String courseId,
        String userId,
        String status,
        Instant createdAt
) {
    public static EnrollmentResponseDto from(Enrollment enrollment) {
        return new EnrollmentResponseDto(
                enrollment.getId(),
                enrollment.getTenantId(),
                enrollment.getCourseId(),
                enrollment.getUserId(),
                enrollment.getStatus().name(),
                enrollment.getCreatedAt());
    }
}
