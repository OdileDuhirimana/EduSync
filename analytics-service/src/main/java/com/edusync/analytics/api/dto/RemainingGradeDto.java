package com.edusync.analytics.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record RemainingGradeDto(
        String name,
        @DecimalMin("0.0") @DecimalMax("100.0") Double weightPct
) {
}
