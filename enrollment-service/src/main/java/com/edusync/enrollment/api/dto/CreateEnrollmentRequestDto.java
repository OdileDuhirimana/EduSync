package com.edusync.enrollment.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * WHY userId is optional here rather than @NotBlank: the caller (a student)
 * may enroll themselves, in which case the controller falls back to the
 * X-User-Id header, mirroring the original controller's behavior. An
 * ADMIN/INSTRUCTOR caller may instead enroll a different target user by
 * supplying it explicitly. EnrollmentService still enforces that the
 * resolved target user id is non-blank (BAD_REQUEST otherwise).
 */
public record CreateEnrollmentRequestDto(
        @NotBlank
        String courseId,

        String userId
) {
}
