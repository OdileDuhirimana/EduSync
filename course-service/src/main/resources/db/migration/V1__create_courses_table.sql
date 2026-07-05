-- WHY Flyway instead of Hibernate's ddl-auto=update: DB-04 in both audits
-- flagged the complete absence of any migration tool, meaning schema
-- evolution had no history, no repeatability across environments, and no
-- safe path to review schema changes in a pull request. ddl-auto is
-- disabled in application.yml; this file is now the only way the schema
-- changes, and every future change must be a new versioned migration.
CREATE TABLE courses (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- Enforces course-code uniqueness at the database level (not just in
-- application code), and doubles as the lookup index for findByCode/
-- existsByCode.
CREATE UNIQUE INDEX ux_courses_code ON courses (code);

-- Supports the paginated "list courses by status" query path.
CREATE INDEX ix_courses_status ON courses (status);
