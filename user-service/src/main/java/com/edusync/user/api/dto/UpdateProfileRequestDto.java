package com.edusync.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequestDto(
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
