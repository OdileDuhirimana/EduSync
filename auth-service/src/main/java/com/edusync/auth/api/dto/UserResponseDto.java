package com.edusync.auth.api.dto;

import com.edusync.auth.domain.User;

public record UserResponseDto(String id, String email, String firstName, String lastName) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(user.id(), user.email(), user.firstName(), user.lastName());
    }
}
