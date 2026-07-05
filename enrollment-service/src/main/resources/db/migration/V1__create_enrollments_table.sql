-- WHY Flyway instead of Hibernate's ddl-auto=update: DB-04 in both audits
-- flagged the complete absence of any migration tool, meaning schema
-- evolution had no history, no repeatability across environments, and no
-- safe path to review schema changes in a pull request. ddl-auto is
-- disabled in application.yml; this file is now the only way the schema
-- changes, and every future change must be a new versioned migration.
CREATE TABLE enrollments (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    course_id   VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- Supports the paginated "my enrollments" query path
-- (findByTenantIdAndUserId), which is filtered by exactly these two columns
-- on every call — without this index that query would degrade to a full
-- table scan as enrollment volume grows (the exact SCALE class of defect
-- API-05/06/07 in the audits called out).
CREATE INDEX ix_enrollments_tenant_user ON enrollments (tenant_id, user_id);

-- Business rule: a user must not hold two simultaneous ENROLLED rows for the
-- same course in the same tenant. This is a table-wide unique constraint on
-- the full (tenant_id, course_id, user_id) triple rather than a constraint
-- filtered to status='ENROLLED' rows only, because the H2 2.2.x engine this
-- project runs on does not support partial/filtered unique indexes
-- (verified against the installed h2 2.2.224 driver: `CREATE UNIQUE INDEX
-- ... WHERE ...` is a syntax error). Enrollment#drop is therefore modeled as
-- a soft-delete status transition (see Enrollment's class Javadoc), and
-- EnrollmentService#create reactivates an existing DROPPED row for the same
-- triple instead of inserting a second one when a user re-enrolls — this
-- constraint is what makes that reactivation-not-insert behavior mandatory,
-- not just a convenience, and it is enforced here as defense in depth in
-- addition to the application-level check in EnrollmentService#create
-- (DB-01: never rely solely on application-level checks for data integrity).
CREATE UNIQUE INDEX ux_enrollments_tenant_course_user ON enrollments (tenant_id, course_id, user_id);
