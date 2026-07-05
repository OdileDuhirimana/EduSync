package com.edusync.auth.domain;

import java.util.List;

/**
 * WHY this moved out of AuthController (was a private record nested inside
 * the controller): ARC-01/AR-03 in the audits flagged that domain state was
 * defined and stored directly inside controller classes with no separate
 * domain/persistence layer. Promoting it to a top-level domain type is what
 * allows UserRepository to have a real, testable contract independent of
 * any specific controller.
 */
public record User(
        String id,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        List<String> roles
) {
}
