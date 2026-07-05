package com.edusync.auth.repository;

import com.edusync.auth.domain.RefreshToken;

import java.util.Optional;

/** See {@link UserRepository} for the rationale behind an in-memory-backed interface at this stage. */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    /** Atomically removes and returns the token, so a stolen/replayed refresh token can only ever be used once. */
    Optional<RefreshToken> consume(String token);

    void deleteByToken(String token);
}
