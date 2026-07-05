package com.edusync.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @Email @NotBlank String email,

        // WHY @Size(min = 8) was added: SEC-01 in the portfolio evaluation
        // flagged that a 1-character password was accepted because only
        // @NotBlank was present. Minimum length is a baseline strength
        // control; full complexity rules are out of scope for this pass.
        @NotBlank @Size(min = 8, max = 128) String password,

        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
