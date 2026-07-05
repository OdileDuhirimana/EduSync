package com.edusync.common.security;

import java.util.List;

/**
 * Typed view over the identity headers injected by the gateway's
 * JwtAuthenticationFilter after verifying the caller's JWT.
 *
 * WHY this exists: without it, every controller method would repeat
 * `@RequestHeader(value = "X-User-Roles", required = false) String roles`
 * plus ad hoc `roles.contains("INSTRUCTOR")` string checks (exactly the
 * duplication flagged by CODE-04/DOM-02 in the audits). Controllers now
 * build one CallerContext per request and delegate authorization decisions
 * to {@link #requireRole(Role...)} / {@link #hasRole(Role)}.
 */
public record CallerContext(String userId, String email, List<Role> roles, String tenantId) {

    public static CallerContext fromHeaders(String userId, String email, String rolesHeader, String tenantId) {
        return new CallerContext(
                userId,
                email,
                Role.parseHeaderValue(rolesHeader),
                tenantId == null || tenantId.isBlank() ? "default" : tenantId);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Role... candidates) {
        for (Role candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public void requireRole(Role... anyOf) {
        if (!hasAnyRole(anyOf)) {
            throw new ForbiddenExceptionForRoles(anyOf);
        }
    }

    /** Internal helper so requireRole can build a descriptive message without duplicating it at every call site. */
    private static final class ForbiddenExceptionForRoles extends com.edusync.common.error.ForbiddenException {
        ForbiddenExceptionForRoles(Role[] anyOf) {
            super("Requires one of roles: " + java.util.Arrays.toString(anyOf));
        }
    }
}
