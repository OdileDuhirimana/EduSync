package com.edusync.auth.api.dto;

public record TokenPairResponseDto(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
