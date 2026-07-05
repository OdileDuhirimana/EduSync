package com.edusync.submission.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JPA entity backing real persistence for student submissions.
 *
 * WHY this replaces the previous private inline `record Submission(...)` held
 * in a ConcurrentHashMap field on the controller: that design lost every
 * submitted answer and every plagiarism-comparison input on restart, and
 * could not run as more than one instance. submission-service was flagged in
 * the audit as one of the two most sensitive services in the whole system
 * (alongside grading-service) precisely because it holds submitted academic
 * work — losing it on restart is a materially worse defect here than the
 * equivalent gap in course-service/enrollment-service. This entity is mapped
 * by Flyway migration V1__create_submissions_table.sql, which is the single
 * source of truth for the schema.
 *
 * WHY `normalizedAnswerText` is stored as its own column rather than
 * recomputed on read: it is the exact text the Jaccard-similarity algorithm
 * compares against. Persisting it lets SubmissionService#computeSimilarity
 * fetch only the same-assessment candidate rows it needs
 * (SubmissionRepository#findByAssessmentIdAndIdNot) instead of loading and
 * re-normalizing every submission in the table in Java on every similarity
 * check — the fix for the original controller's
 * `store.values().stream().filter(...)` full-table-scan pattern.
 */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "assessment_id", nullable = false, length = 64)
    private String assessmentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // See AnswersJsonConverter's class Javadoc for why this is a JSON column
    // rather than a normalized relational schema.
    @Lob
    @Convert(converter = AnswersJsonConverter.class)
    @Column(name = "answers", nullable = false)
    private List<Map<String, Object>> answers;

    @Lob
    @Column(name = "normalized_answer_text", nullable = false)
    private String normalizedAnswerText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubmissionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Submission() {
        // required by JPA
    }

    public Submission(String id, String assessmentId, String userId, List<Map<String, Object>> answers,
                       String normalizedAnswerText, SubmissionStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.assessmentId = assessmentId;
        this.userId = userId;
        this.answers = answers;
        this.normalizedAnswerText = normalizedAnswerText;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public List<Map<String, Object>> getAnswers() {
        return answers;
    }

    public String getNormalizedAnswerText() {
        return normalizedAnswerText;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
