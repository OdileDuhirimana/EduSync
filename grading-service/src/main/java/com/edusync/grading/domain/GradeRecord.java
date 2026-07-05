package com.edusync.grading.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JPA entity backing real persistence for a submission's grade.
 *
 * WHY this replaces the previous `record GradeRecord(...)` held in a
 * ConcurrentHashMap field on GradingController: that design lost all data on
 * every restart and could not run as more than one instance (DB-01 through
 * DB-08, SEC-01, SCALE-05 in the audits — the same finding already fixed for
 * course-service/enrollment-service). This entity is mapped by Flyway
 * migration V1__create_grading_tables.sql, which is the single source of
 * truth for the schema (see that file for column constraints/indexes).
 *
 * WHY the per-question breakdown is a child table (`grade_breakdown_items`)
 * via @ElementCollection rather than a single JSON string column: a JSON
 * text column would let Hibernate's `ddl-auto=validate` pass while silently
 * storing structurally invalid data (no column types, no per-entry
 * constraints, unqueryable without JSON functions H2 supports inconsistently
 * across versions). A child table is standard portable JPA, gives every
 * (submission, question) score its own typed `points` column, and requires
 * no custom AttributeConverter to maintain. The tradeoff is one extra table
 * and an extra join on read — an acceptable cost for a value this small.
 *
 * WHY one row per submission (unique `submission_id`) rather than an
 * append-only grading history table: this pass models "the current grade for
 * a submission," matching the previous ConcurrentHashMap's semantics
 * (`grades.put(submissionId, record)` always overwrote). An audit trail of
 * every historical grading pass is a reasonable follow-up but is out of
 * scope here — see GradingService for how re-grading (auto or manual)
 * updates this same row in place instead of inserting a new one.
 */
@Entity
@Table(name = "grade_records")
public class GradeRecord {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "submission_id", nullable = false, unique = true, length = 64)
    private String submissionId;

    // WHY FetchType.EAGER (fixes a real bug found via live integration testing,
    // not covered by GradingServiceTest's mocked-repository unit tests): with
    // the JPA default of LAZY, any GradeRecord fetched fresh from the
    // repository (as opposed to one just mutated in-process by
    // applyGrading/applyOverride, whose `breakdown` field is a plain
    // LinkedHashMap already) held a Hibernate-backed PersistentMap proxy for
    // `breakdown`. GradeResponseDto.from(...) calls getBreakdown() from
    // GradingController — outside the @Transactional method that fetched the
    // entity, and this module runs with `spring.jpa.open-in-view: false`, so
    // the Hibernate Session is already closed by the time the controller
    // serializes the response. That combination threw
    // org.hibernate.LazyInitializationException ("could not initialize
    // proxy - no Session"), turning into an unhandled 500 on every endpoint
    // that serializes a freshly-fetched GradeRecord: publish,
    // regrade/{id}/request, regrade/{id}/decision, and GET regrade/{id} (auto
    // grade/manual grade were unaffected only because they hand back the
    // same in-memory entity they had just mutated in the same call, whose
    // `breakdown` was never lazily loaded from the DB to begin with).
    // EAGER is the correct fix, not a workaround: `breakdown` is a small,
    // always-needed value (see the class Javadoc's own "extra join on read
    // is an acceptable cost for a value this small" rationale for why this
    // child table exists at all) that every current caller reads on every
    // fetch — there is no code path that loads a GradeRecord without also
    // needing its breakdown, so there is no lazy-loading benefit being given
    // up here, only a footgun being removed.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grade_breakdown_items", joinColumns = @JoinColumn(name = "grade_record_id"))
    @MapKeyColumn(name = "item_key", length = 128)
    @Column(name = "points", nullable = false)
    private Map<String, Integer> breakdown = new LinkedHashMap<>();

    @Column(nullable = false)
    private int total;

    @Column(length = 2000)
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GradeRecord() {
        // required by JPA
    }

    public GradeRecord(String id, String submissionId, Map<String, Integer> breakdown, int total,
                        String feedback, GradeStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.submissionId = submissionId;
        this.breakdown = new LinkedHashMap<>(breakdown);
        this.total = total;
        this.feedback = feedback;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    /** Defensive copy: callers cannot mutate this entity's internal state through the returned map. */
    public Map<String, Integer> getBreakdown() {
        return Map.copyOf(breakdown);
    }

    public int getTotal() {
        return total;
    }

    public String getFeedback() {
        return feedback;
    }

    public GradeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Applies the result of a fresh auto-grade or manual-grade pass, replacing
     * the previous breakdown/total/feedback outright and resetting status to
     * GRADED — a new grading pass supersedes any earlier instructor override,
     * which mirrors the previous controller's behavior of unconditionally
     * overwriting the stored record on every /auto or /manual call.
     */
    public void applyGrading(Map<String, Integer> breakdown, int total, String feedback, Instant when) {
        this.breakdown = new LinkedHashMap<>(breakdown);
        this.total = total;
        this.feedback = feedback;
        this.status = GradeStatus.GRADED;
        this.updatedAt = when;
    }

    /**
     * Applies a moderator-approved regrade override total. WHY this only
     * changes total/status and leaves the per-question breakdown untouched:
     * an override total represents a holistic moderator judgment call (e.g.
     * "the rubric was too strict on Q3, bump the total"), not a re-scoring of
     * individual questions — GradingService#decideRegrade is the only caller.
     */
    public void applyOverride(int total, Instant when) {
        this.total = total;
        this.status = GradeStatus.GRADED_OVERRIDDEN;
        this.updatedAt = when;
    }
}
