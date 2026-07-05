-- WHY a new versioned migration instead of editing V1: Flyway migrations are
-- immutable once applied to any environment; adding a column is itself a
-- real-world schema-evolution exercise, exactly the discipline DB-04 in the
-- code review required ("no repeatability across environments, no safe path
-- to review schema changes"). This closes SEC-04 in the portfolio evaluation:
-- "course-service's Course entity has no owner/instructor-id field at all...
-- any instructor can publish any other instructor's course."
--
-- WHY a DEFAULT value instead of a bare NOT NULL: this migration must remain
-- safe to run against an environment that already has course rows from
-- before this change (backward-compatible schema evolution, not just a
-- greenfield assumption). 'unassigned' is a deliberately obvious sentinel,
-- not a real user id, so any pre-existing row is visibly flagged rather than
-- silently attributed to an arbitrary instructor. Every course created after
-- this migration always supplies a real instructor id (see
-- CourseService#create), so this default is a one-time migration safety net,
-- not a normal code path.
ALTER TABLE courses ADD COLUMN instructor_id VARCHAR(64) NOT NULL DEFAULT 'unassigned';

-- Supports CourseService#publish's ownership check pattern (look up by id,
-- compare instructor_id) and any future "my courses" listing endpoint.
CREATE INDEX ix_courses_instructor ON courses (instructor_id);
