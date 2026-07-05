package com.edusync.common.security;

import java.util.List;

/**
 * Names of the identity headers that downstream services trust unconditionally.
 *
 * These headers are only trustworthy because the gateway (a) strips any
 * client-supplied copies before routing and (b) re-derives them itself from a
 * cryptographically verified JWT. A service that receives one of these headers
 * directly from the public internet (bypassing the gateway) would be exposed
 * again — deploying services so only the gateway is publicly reachable is a
 * documented deployment requirement (see README "Security Model" section).
 */
public final class TrustedHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String TENANT_ID = "X-Tenant-Id";

    public static final List<String> ALL = List.of(USER_ID, USER_EMAIL, USER_ROLES, TENANT_ID);

    private TrustedHeaders() {
    }
}
