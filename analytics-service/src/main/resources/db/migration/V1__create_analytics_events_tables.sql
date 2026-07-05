-- WHY Flyway instead of Hibernate's ddl-auto=update: matches
-- course-service/enrollment-service — schema evolution needs history,
-- repeatability across environments, and a reviewable path for changes.
-- ddl-auto is set to "validate" in application.yml; this file is the only
-- way the schema changes.
--
-- WHY these three tables exist at all: they are the real, persisted backing
-- store for the /engagement, /grade-distribution and /funnels aggregate
-- endpoints, replacing the hardcoded static responses the code review
-- flagged as "fixtures masquerading as computed responses" (see
-- AnalyticsService's class Javadoc for the full ingestion-then-aggregate
-- design rationale).

CREATE TABLE engagement_events (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_id   VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(32)  NOT NULL,
    occurred_at TIMESTAMP    NOT NULL
);

-- Supports simple "all events for a course" lookups.
CREATE INDEX ix_engagement_events_course ON engagement_events (course_id);

-- Covers AnalyticsService#engagement's dau query
-- (countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter), which
-- filters on exactly these three columns on every call — without this
-- composite index that query would degrade to a full table scan as
-- engagement volume grows.
CREATE INDEX ix_engagement_events_course_type_time ON engagement_events (course_id, event_type, occurred_at);

CREATE TABLE grade_record_snapshots (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_id    VARCHAR(64)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL,
    letter_grade VARCHAR(8)   NOT NULL,
    recorded_at  TIMESTAMP    NOT NULL
);

CREATE INDEX ix_grade_snapshots_course ON grade_record_snapshots (course_id);

-- Covers AnalyticsService#gradeDistribution's per-letter-grade count query
-- (countByCourseIdAndLetterGrade), called once per LetterGrade value.
CREATE INDEX ix_grade_snapshots_course_grade ON grade_record_snapshots (course_id, letter_grade);

CREATE TABLE funnel_events (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_id   VARCHAR(64)  NOT NULL,
    stage       VARCHAR(16)  NOT NULL,
    occurred_at TIMESTAMP    NOT NULL
);

CREATE INDEX ix_funnel_events_course ON funnel_events (course_id);

-- Covers AnalyticsService#funnels' and #engagement's per-stage count query
-- (countByCourseIdAndStage).
CREATE INDEX ix_funnel_events_course_stage ON funnel_events (course_id, stage);
