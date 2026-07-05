package com.edusync.auth.repository;

import com.edusync.auth.domain.RefreshToken;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<String, RefreshToken> store = new ConcurrentHashMap<>();

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        store.put(refreshToken.token(), refreshToken);
        return refreshToken;
    }

    @Override
    public Optional<RefreshToken> consume(String token) {
        return Optional.ofNullable(store.remove(token));
    }

    @Override
    public void deleteByToken(String token) {
        store.remove(token);
    }
}
