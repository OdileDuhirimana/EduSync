-- WHY Flyway instead of Hibernate's ddl-auto=update: see the identical
-- rationale in course-service/V1__create_courses_table.sql and
-- enrollment-service/V1__create_enrollments_table.sql. ddl-auto is disabled
-- in application.yml; this file is now the only way the schema changes.
--
-- WHY `answers` and `normalized_answer_text` are CLOB rather than VARCHAR:
-- `answers` stores arbitrary, per-assessment, schema-less nested JSON (see
-- the WHY comment on Submission#answers / AnswersJsonConverter for why a
-- normalized relational schema was rejected for this column) and
-- `normalized_answer_text` is derived free text used as similarity-comparison
-- input, both of which can exceed a reasonable VARCHAR bound for
-- long-form/essay-style answers.
CREATE TABLE submissions (
    id                       VARCHAR(36)  NOT NULL PRIMARY KEY,
    assessment_id            VARCHAR(64)  NOT NULL,
    user_id                  VARCHAR(64)  NOT NULL,
    answers                  CLOB         NOT NULL,
    normalized_answer_text   CLOB         NOT NULL,
    status                   VARCHAR(16)  NOT NULL,
    created_at               TIMESTAMP    NOT NULL,
    updated_at               TIMESTAMP    NOT NULL
);

-- Supports SubmissionRepository#findByAssessmentIdAndIdNot, used by the
-- similarity endpoint to fetch only same-assessment candidates from the
-- database. Without this index that query would degrade to a full table
-- scan as submission volume grows — this is the fix for the original
-- controller's `store.values().stream().filter(...)` in-memory full-table-scan
-- pattern (the SCALE-03/04 class of defect also called out in the
-- enrollment-service and course-service migrations).
CREATE INDEX ix_submissions_assessment ON submissions (assessment_id);
