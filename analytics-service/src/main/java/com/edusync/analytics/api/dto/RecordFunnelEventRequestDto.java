package com.edusync.analytics.api.dto;

import com.edusync.analytics.domain.FunnelStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordFunnelEventRequestDto(
        @NotBlank String courseId,
        @NotNull FunnelStage stage
) {
}
