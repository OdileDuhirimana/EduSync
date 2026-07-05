package com.edusync.course.api.dto;

import com.edusync.course.domain.Course;

import java.time.Instant;

public record CourseResponseDto(
        String id,
        String code,
        String title,
        String instructorId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CourseResponseDto from(Course course) {
        return new CourseResponseDto(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getInstructorId(),
                course.getStatus().name(),
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
