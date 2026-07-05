-- WHY Flyway instead of Hibernate's ddl-auto=update: mirrors the same
-- rationale used across every remediated service in this monorepo — schema
-- evolution needs history, repeatability across environments, and a
-- reviewable diff in pull requests. ddl-auto is set to "validate" in
-- application.yml, so this file is the only way this table's schema changes.
--
-- `id` is NOT a database-generated key. It is the exact userId string value
-- the gateway asserts via the trusted X-User-Id header (originally sourced
-- from auth-service's account record at signup/login time), used directly
-- as this table's primary key so a profile row can always be looked up by
-- the same identity the gateway verified for the caller. There is no
-- foreign key to auth-service's user table — the two services intentionally
-- own separate data (auth-service: credentials, user-service: profile) and
-- are not permitted a compile-time or schema-level dependency on each other.
CREATE TABLE user_profiles (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    email       VARCHAR(255),
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    -- Backs real JPA optimistic locking (@Version on UserProfile), replacing
    -- the old response field that minted a random UUID per request and
    -- called it "version" without it ever protecting anything.
    version     BIGINT       NOT NULL DEFAULT 0
);
