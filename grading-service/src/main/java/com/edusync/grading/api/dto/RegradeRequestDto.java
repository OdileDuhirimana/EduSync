package com.edusync.grading.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /grading/regrade/{submissionId}/request}.
 *
 * WHY {@code requestedBy} is not a field here (unlike the previous
 * `RegradeRequestBody` record, which trusted a client-supplied
 * `requestedBy` string outright): that was a spoofing gap — any caller could
 * claim to be requesting a regrade on behalf of a different student.
 * GradingController now derives the requester's identity exclusively from
 * the gateway-verified `X-User-Id` header (see GradingController#requestRegrade),
 * which cannot be forged by a client body field.
 */
public record RegradeRequestDto(
        @NotBlank String reason
) {
}
