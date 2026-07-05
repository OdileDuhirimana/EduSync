package com.edusync.course.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCourseRequestDto(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "code may only contain letters, digits, '.', '_' and '-'")
        String code,

        @NotBlank
        @Size(max = 200)
        String title
) {
}
