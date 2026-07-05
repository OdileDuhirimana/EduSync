package com.edusync.analytics.api.dto;

import com.edusync.analytics.domain.LetterGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordGradeSnapshotRequestDto(
        @NotBlank String courseId,
        @NotBlank String userId,
        @NotNull LetterGrade letterGrade
) {
}
