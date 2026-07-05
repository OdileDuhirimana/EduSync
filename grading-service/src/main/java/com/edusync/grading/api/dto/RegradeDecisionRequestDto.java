package com.edusync.grading.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /grading/regrade/{requestId}/decision}.
 *
 * WHY {@code moderatorId} is not a field here (unlike the previous
 * `RegradeDecisionBody` record): same spoofing gap as
 * {@link RegradeRequestDto#reason()} — GradingController derives the
 * deciding moderator's identity from the gateway-verified `X-User-Id`
 * header, not a client-supplied body field.
 *
 * WHY {@code decision} is validated in GradingService rather than with a
 * {@code @Pattern} here: the invalid-value error needs to surface as this
 * service's own {@code BAD_REQUEST}/{@code INVALID_DECISION} business-rule
 * error (a named, testable outcome — see GradingServiceTest), not as a
 * generic Bean Validation {@code VALIDATION_ERROR} field violation, so the
 * distinction between "malformed request" and "semantically invalid
 * decision" stays meaningful to API clients.
 */
public record RegradeDecisionRequestDto(
        @NotBlank String decision,
        String note,
        @Min(0) @Max(100) Integer overrideTotal
) {
}
