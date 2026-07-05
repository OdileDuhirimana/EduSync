package com.edusync.common.security;

import java.util.List;
import java.util.Locale;

/**
 * WHY a typed enum instead of raw string matching: DOM-03 in the code review
 * flagged that authorization checks were raw `roles.contains("INSTRUCTOR")`
 * substring tests on a client-controlled string with no shared, type-safe
 * permission model anywhere in the codebase (copy-pasted independently in
 * CourseController and EnrollmentController). Centralizing the role model
 * here means every service compares against the same enum instead of
 * re-implementing string matching, and a typo in a role name fails to
 * compile rather than silently granting/denying access incorrectly.
 */
public enum Role {
    STUDENT,
    INSTRUCTOR,
    ADMIN;

    /** Parses the comma-separated X-User-Roles header value set by the gateway. */
    public static List<Role> parseHeaderValue(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of();
        }
        return List.of(rolesHeader.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .map(Role::valueOfSafe)
                .filter(r -> r != null)
                .toList();
    }

    private static Role valueOfSafe(String name) {
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
