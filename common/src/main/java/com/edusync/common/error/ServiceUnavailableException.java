package com.edusync.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a downstream service call (e.g. enrollment-service calling
 * course-service to verify a course exists/is published — see
 * EnrollmentService#create) fails for a transport reason: timeout, connection
 * refused, or an unexpected non-2xx/404 response that isn't a meaningful
 * business-rule rejection.
 *
 * WHY this exists (closes part of ERR-04 in the code review — "no
 * inter-service network-failure handling exists... a downstream service
 * outage would surface as a raw 502/504 to the client with no graceful
 * handling"): without a typed exception here, a downstream outage would
 * either propagate as an unhandled `RestClientException` (caught only by
 * {@link GlobalExceptionHandler}'s generic 500 fallback, which is technically
 * safe but semantically wrong — a 500 implies *this* service is broken, not
 * that a peer is unreachable) or, worse, be silently swallowed. Mapping it to
 * HTTP 503 lets a caller distinguish "the course doesn't exist / isn't
 * published" (404/409, a real business answer) from "we couldn't find out
 * right now" (503, a transient infrastructure problem worth retrying).
 * A full circuit breaker (Resilience4j) is a documented follow-up — see
 * README "Known Limitations" — not implemented here to stay within this
 * project's pre-approved dependency list.
 */
public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
