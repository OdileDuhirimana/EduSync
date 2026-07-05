package com.edusync.assessment.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity recording that a specific user started a timed window on a
 * specific assessment.
 *
 * WHY this needs to be a real, persisted entity rather than an ephemeral
 * response computed on the fly: the original `/assessments/{id}/start`
 * endpoint minted a random token and returned it, but never recorded that a
 * session had started — there was no way to later answer "did this user
 * actually start this assessment, and when", which a timed-assessment
 * feature genuinely needs (e.g. to enforce a submission deadline derived
 * from startedAt + timeLimitMinutes). Persisting it here is also what makes
 * AssessmentService#start's idempotency rule possible: see that method's
 * Javadoc and V1__create_assessments_table.sql's unique index on
 * (assessment_id, user_id) for why a student cannot mint a second, longer
 * time window by calling start twice.
 */
@Entity
@Table(name = "assessment_sessions")
public class AssessmentSession {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "assessment_id", nullable = false, length = 36)
    private String assessmentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "time_limit_minutes", nullable = false)
    private int timeLimitMinutes;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    protected AssessmentSession() {
        // required by JPA
    }

    public AssessmentSession(String id, String assessmentId, String userId, String token,
                              int timeLimitMinutes, Instant startedAt) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.userId = userId;
        this.token = token;
        this.timeLimitMinutes = timeLimitMinutes;
        this.startedAt = startedAt;
    }

    public String getId() {
        return id;
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public String getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public int getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
