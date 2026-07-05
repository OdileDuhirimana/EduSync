package com.edusync.auth.repository;

import com.edusync.auth.domain.User;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WHY this class is small and does nothing but map storage operations: it is
 * intentionally the ONLY class in auth-service that knows the storage is a
 * ConcurrentHashMap. AuthService depends on the {@link UserRepository}
 * interface, so replacing this class with a JPA-backed implementation later
 * is a drop-in swap with zero changes to business logic or tests that mock
 * the interface.
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(byEmail.get(normalize(email)));
    }

    @Override
    public Optional<User> findById(String id) {
        return byEmail.values().stream()
                .filter(u -> Objects.equals(u.id(), id))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return byEmail.containsKey(normalize(email));
    }

    @Override
    public User save(User user) {
        byEmail.put(normalize(user.email()), user);
        return user;
    }

    private String normalize(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }
}
