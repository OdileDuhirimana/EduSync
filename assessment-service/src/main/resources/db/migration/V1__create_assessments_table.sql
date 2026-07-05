-- WHY Flyway instead of Hibernate's ddl-auto=update: matches the DB-04
-- remediation already applied to course-service/enrollment-service —
-- schema evolution needs history, repeatability across environments, and a
-- safe path to review schema changes in a pull request. ddl-auto is
-- disabled in application.yml; this file is now the only way the schema
-- changes, and every future change must be a new versioned migration.
CREATE TABLE assessments (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_id   VARCHAR(64)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    type        VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- Supports lookups/listings of assessments by course (the natural access
-- pattern for a course's assessment list), avoiding a full table scan as
-- assessment volume grows.
CREATE INDEX ix_assessments_course_id ON assessments (course_id);

CREATE TABLE assessment_sessions (
    id                 VARCHAR(36)  NOT NULL PRIMARY KEY,
    assessment_id      VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,
    token              VARCHAR(64)  NOT NULL,
    time_limit_minutes INT          NOT NULL,
    started_at         TIMESTAMP    NOT NULL,
    CONSTRAINT fk_assessment_sessions_assessment
        FOREIGN KEY (assessment_id) REFERENCES assessments (id)
);

-- Business rule enforced at the database level (defense in depth, on top of
-- the application-level check in AssessmentService#start): a user must not
-- hold two session rows for the same assessment. This is what guarantees
-- AssessmentService#start's idempotency rule ("return the existing
-- session/token instead of minting a new one") can never be bypassed by a
-- race between two concurrent /start calls for the same
-- (assessmentId, userId) pair — the second INSERT would violate this
-- constraint even if the application-level existence check raced ahead of
-- it (DB-01: never rely solely on application-level checks for data
-- integrity, the same reasoning EnrollmentService's unique index follows).
CREATE UNIQUE INDEX ux_assessment_sessions_assessment_user
    ON assessment_sessions (assessment_id, user_id);
