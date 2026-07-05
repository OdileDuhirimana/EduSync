package com.edusync.common.security;

import com.edusync.common.error.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WHY these tests exist: {@link CallerContext#requireRole(Role...)} is the
 * single authorization primitive every remediated controller in this
 * monorepo (Course/Enrollment/Grading/Assessment/Analytics) calls to enforce
 * role checks. A regression here would silently weaken authorization across
 * every service simultaneously, which is exactly the kind of shared,
 * security-critical code the portfolio evaluation's AR-05 finding said must
 * not ship untested.
 */
class CallerContextTest {

    @Test
    void fromHeadersDefaultsTenantIdWhenBlank() {
        CallerContext caller = CallerContext.fromHeaders("user-1", "u@example.com", "STUDENT", null);
        assertThat(caller.tenantId()).isEqualTo("default");
    }

    @Test
    void fromHeadersPreservesExplicitTenantId() {
        CallerContext caller = CallerContext.fromHeaders("user-1", null, null, "tenant-42");
        assertThat(caller.tenantId()).isEqualTo("tenant-42");
    }

    @Test
    void hasRoleReflectsParsedRoles() {
        CallerContext caller = CallerContext.fromHeaders("user-1", null, "STUDENT,INSTRUCTOR", null);
        assertThat(caller.hasRole(Role.STUDENT)).isTrue();
        assertThat(caller.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void hasAnyRoleMatchesIfAtLeastOneCandidateIsPresent() {
        CallerContext caller = CallerContext.fromHeaders("user-1", null, "STUDENT", null);
        assertThat(caller.hasAnyRole(Role.ADMIN, Role.STUDENT)).isTrue();
        assertThat(caller.hasAnyRole(Role.ADMIN, Role.INSTRUCTOR)).isFalse();
    }

    @Test
    void requireRolePassesSilentlyWhenCallerHoldsOneOfTheRequiredRoles() {
        CallerContext caller = CallerContext.fromHeaders("user-1", null, "INSTRUCTOR", null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);
        // no exception thrown = pass
    }

    @Test
    void requireRoleThrowsForbiddenWhenCallerHoldsNoneOfTheRequiredRoles() {
        CallerContext caller = CallerContext.fromHeaders("user-1", null, "STUDENT", null);
        assertThatThrownBy(() -> caller.requireRole(Role.INSTRUCTOR, Role.ADMIN))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireRoleThrowsForbiddenWhenNoRolesHeaderWasPresentAtAll() {
        // WHY this is the specific exploit path SEC-02/AUTH-04 named: a caller
        // with no verified role at all (e.g. the gateway never authenticated
        // them) must never silently pass a role gate.
        CallerContext caller = CallerContext.fromHeaders("user-1", null, null, null);
        assertThatThrownBy(() -> caller.requireRole(Role.INSTRUCTOR, Role.ADMIN))
                .isInstanceOf(ForbiddenException.class);
    }
}
