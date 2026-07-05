-- WHY Flyway instead of Hibernate's ddl-auto=update: DB-04 in both audits
-- flagged the complete absence of any migration tool, meaning schema
-- evolution had no history, no repeatability across environments, and no
-- safe path to review schema changes in a pull request. ddl-auto is
-- disabled in application.yml; this file is now the only way the schema
-- changes, and every future change must be a new versioned migration.

-- One row per submission's current grade (see GradeRecord's class Javadoc
-- for why this models "current grade," not an append-only grading history).
CREATE TABLE grade_records (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    submission_id VARCHAR(64)   NOT NULL,
    total         INT           NOT NULL,
    feedback      VARCHAR(2000),
    status        VARCHAR(20)   NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL
);

-- Enforces "one current grade per submission" at the database level (not
-- just in application code), and doubles as the lookup index backing
-- GradeRecordRepository#findBySubmissionId, called on every grading,
-- publish, and regrade-request request.
CREATE UNIQUE INDEX ux_grade_records_submission_id ON grade_records (submission_id);

-- Child table holding the per-question point breakdown for a grade record,
-- backing GradeRecord's @ElementCollection-mapped `breakdown` field. See
-- GradeRecord's class Javadoc for why this is a typed child table rather
-- than a single JSON text column.
CREATE TABLE grade_breakdown_items (
    grade_record_id VARCHAR(36)  NOT NULL REFERENCES grade_records (id) ON DELETE CASCADE,
    item_key        VARCHAR(128) NOT NULL,
    points          INT          NOT NULL,
    PRIMARY KEY (grade_record_id, item_key)
);

-- Regrade moderation cases: PENDING -> APPROVED/REJECTED, decided by an
-- instructor/admin. See RegradeCase's class Javadoc for why requested_by/
-- decided_by are plain columns rather than foreign keys.
CREATE TABLE regrade_cases (
    id             VARCHAR(36)   NOT NULL PRIMARY KEY,
    submission_id  VARCHAR(64)   NOT NULL,
    requested_by   VARCHAR(64)   NOT NULL,
    reason         VARCHAR(2000) NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    requested_at   TIMESTAMP     NOT NULL,
    decided_by     VARCHAR(64),
    decision_note  VARCHAR(2000),
    decided_at     TIMESTAMP,
    override_total INT
);

-- Supports "find the regrade case(s) for this submission" query paths and
-- the NOT NULL FK-like relationship back to grade_records (not a real FK
-- constraint: a regrade case is intentionally kept even if, hypothetically,
-- its grade were ever removed, preserving the moderation audit trail).
CREATE INDEX ix_regrade_cases_submission_id ON regrade_cases (submission_id);
