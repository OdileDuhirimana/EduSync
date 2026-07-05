package com.edusync.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity backing real persistence for a regrade moderation case.
 *
 * WHY this replaces the previous `record RegradeCase(...)` held in a
 * ConcurrentHashMap field on GradingController: same DB-01 through DB-08 /
 * SEC-01 / SCALE-05 rationale as {@link GradeRecord}. Mapped by Flyway
 * migration V1__create_grading_tables.sql.
 *
 * WHY `requestedBy`/`decidedBy` are plain columns rather than foreign keys
 * to a users table: this service has no compile-time dependency on
 * user-service in this pass (see GradingController's Javadoc on the
 * auto-grade endpoint for the same "documented follow-up integration,
 * not silently deferred" reasoning applied here) — both fields are set from
 * the gateway-verified `X-User-Id` header, not client-supplied body fields,
 * which is the actual security property that matters (see
 * GradingService#requestRegrade / #decideRegrade).
 */
@Entity
@Table(name = "regrade_cases")
public class RegradeCase {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "submission_id", nullable = false, length = 64)
    private String submissionId;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RegradeStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decision_note", length = 2000)
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "override_total")
    private Integer overrideTotal;

    protected RegradeCase() {
        // required by JPA
    }

    public RegradeCase(String id, String submissionId, String requestedBy, String reason,
                        RegradeStatus status, Instant requestedAt) {
        this.id = id;
        this.submissionId = submissionId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public String getId() {
        return id;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public RegradeStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Integer getOverrideTotal() {
        return overrideTotal;
    }

    /** Transitions a PENDING case to APPROVED. Caller (GradingService) enforces the PENDING precondition. */
    public void approve(String decidedBy, String decisionNote, Integer overrideTotal, Instant when) {
        this.status = RegradeStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.overrideTotal = overrideTotal;
        this.decidedAt = when;
    }

    /** Transitions a PENDING case to REJECTED. Caller (GradingService) enforces the PENDING precondition. */
    public void reject(String decidedBy, String decisionNote, Instant when) {
        this.status = RegradeStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.decidedAt = when;
    }
}
