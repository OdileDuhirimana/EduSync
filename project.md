# EduSync LMS Backend — Implementation Blueprint (Spring Boot + MongoDB)

## 0) Executive Summary

Build a modular, production-grade LMS backend using **Spring Boot (Java 17)** and **MongoDB**, organized as **microservices** behind an **API Gateway** with **JWT (access + refresh)**, **role/permission RBAC**, **async events** (Kafka), **Redis caching**, **WebSocket “live” channels** for real-time grading/announcements, and **observability** (OpenTelemetry + Prometheus + Grafana). Ship with **Docker Compose** for local dev and **Kubernetes** manifests for cloud deploy. Include **Swagger/OpenAPI**, **test coverage**, **seed data**, and **CI/CD**.

---

## 1) Architecture Overview

### 1.1 Services

* **api-gateway**: Spring Cloud Gateway, global rate-limiting, request logging, JWT verification (public key), CORS.
* **auth-service**: User auth (email/password + optional OAuth), issue & rotate JWT + refresh tokens, password reset, session revocation, device tracking.
* **user-service**: Profiles, roles & permissions, org/tenant membership, instructor onboarding, avatar file links.
* **course-service**: Courses, modules, lessons, prerequisites, tags, catalogs, draft/publish workflow.
* **enrollment-service**: Enroll/unenroll, waitlists, cohorts, attendance, progress tracking.
* **assessment-service**: Quizzes/exams/assignments, question banks, rubrics, time windows.
* **submission-service**: Student submissions, plagiarism check (stubbed interface), versioning, grading workflows.
* **grading-service**: Auto-grading for objective questions, rubric scoring for subjective, moderation & regrade requests, gradebook aggregates. Emits events for **real-time grade updates**.
* **analytics-service**: Engagement metrics, course funnels, instructor dashboards (Mongo aggregations + Kafka sinks). Expose timeseries and cohort analytics.
* **notification-service**: Email (SMTP), in-app notifications, WebSocket push; template management (i18n).
* **content-service**: File metadata, signed URLs to S3-compatible storage (e.g., MinIO in dev).
* **search-service**: Text search across courses/content (Mongo Atlas Search or embedded Lucene fallback).
* **realtime-service**: Spring WebSocket/STOMP channels for classes, announcements, live grading events.
* **admin-service**: Tenants, feature flags, quotas, audit logs.

> Cross-cutting: **config-service** (Spring Cloud Config), **discovery-service** (Eureka/Consul), **otel-collector**.

### 1.2 Data Stores

* **MongoDB** (primary): one database per tenant (or tenantId fields) with collections per service.
* **Redis**: caching, rate limits, token blacklists, WebSocket session state.
* **Kafka**: inter-service events (e.g., `GRADE_PUBLISHED`, `ENROLLED`, `COURSE_PUBLISHED`).
* **MinIO/S3**: content uploads.

### 1.3 Security & Auth

* JWT (RS256) with short-lived **access** + long-lived **refresh** tokens.
* RBAC **roles**: `ADMIN`, `INSTRUCTOR`, `STUDENT`, `REVIEWER`, `SUPPORT`.
* **Fine-grained permissions** (e.g., `course:publish`, `grade:override`).
* Tenant isolation via `X-Tenant-Id` header + policy guards.
* Input validation, audit logging, IP allow-lists for admin endpoints.

### 1.4 Real-Time

* WebSocket channels:

    * `/topic/course/{courseId}/announcements`
    * `/user/queue/grades` (private grade updates)
    * `/topic/classroom/{cohortId}/live`
* Backed by Redis for scale-out.

---

## 2) Tech Stack

* **Java 17**, **Spring Boot 3.x**, Spring WebFlux for gateway, Spring Security, Spring Data MongoDB.
* **Maven** (multi-module) or **Gradle** (KTS). Choose Maven here for simplicity.
* **Test**: JUnit 5, Testcontainers (Mongo, Kafka, Redis), Mockito.
* **Docs**: Springdoc OpenAPI 2 (Swagger UI per service).
* **CI/CD**: GitHub Actions (build, unit/integration tests, Docker build & push), optional Argo CD for K8s.
* **Observability**: OpenTelemetry, Prometheus, Grafana, Zipkin/Jaeger.

---

## 3) Monorepo Layout (Maven)

```
edusync/
  pom.xml
  common/                     # shared DTOs, exceptions, utils
  api-gateway/
  discovery-service/
  config-service/
  auth-service/
  user-service/
  course-service/
  enrollment-service/
  assessment-service/
  submission-service/
  grading-service/
  analytics-service/
  notification-service/
  content-service/
  search-service/
  realtime-service/
  admin-service/
  infra/
    docker-compose.yml
    k8s/                      # manifests per service + kustomize/helm (optional)
  scripts/
    seed.sh
    create-tenant.sh
  docs/
    openapi/                  # aggregated YAMLs
    ERD.md
```

---

## 4) Core Schemas (Mongo Collections)

> Use `snake_case` for collection names; Java uses records/lombok.

**User**

```
{
  _id, tenantId, email, passwordHash, roles:[...], permissions:[...],
  profile:{ firstName, lastName, avatarUrl, bio, timezone },
  devices:[{ deviceId, lastSeenAt, ip }],
  createdAt, updatedAt, status
}
```

**Course**

```
{ _id, tenantId, code, title, description, tags:[...],
  owners:[userId], instructors:[userId],
  modules:[{ id, title, order, lessons:[{ id, title, type, contentRef, durationMin }] }],
  status: "DRAFT"|"PUBLISHED"|"ARCHIVED",
  prerequisites:[courseId], catalogVisibility: "PUBLIC"|"PRIVATE",
  createdAt, updatedAt
}
```

**Enrollment**

```
{ _id, tenantId, courseId, userId, cohortId, status:"ENROLLED"|"WAITLIST"|"DROPPED",
  progress:{ completedLessons, lastLessonId, pct },
  createdAt, updatedAt
}
```

**Assessment / Question Bank**

```
{ _id, tenantId, courseId, type:"QUIZ"|"EXAM"|"ASSIGNMENT",
  title, settings:{ timeLimitMin, attempts, dueAt, proctoring:false },
  questions:[{ id, kind:"MCQ"|"TRUE_FALSE"|"SHORT"|"CODE"|"ESSAY",
    prompt, options:[...], answerKey, points, rubricRef }],
  createdBy, createdAt, updatedAt
}
```

**Submission**

```
{ _id, tenantId, assessmentId, userId,
  answers:[{ questionId, response, attachments:[fileId] }],
  status:"SUBMITTED"|"GRADED"|"RETURNED",
  score:{ total, breakdown:[{ questionId, points }] },
  feedback:{ text, files:[fileId] },
  createdAt, updatedAt, attempt
}
```

**Gradebook Aggregate**

```
{ _id, tenantId, courseId, userId, totals:{ pointsEarned, pointsPossible }, letter:"A"|"B"|...,
  updatedAt
}
```

**Notification**

```
{ _id, tenantId, userId, channel:"IN_APP"|"EMAIL"|"PUSH", templateKey, vars:{}, read:false, createdAt }
```

**Content File**

```
{ _id, tenantId, ownerId, path, mime, size, checksum, signedUrlExpiresAt, createdAt }
```

---

## 5) API Contracts (representative)

### Auth Service

* `POST /auth/register` {email,password,firstName,lastName} → 201 user
* `POST /auth/login` {email,password} → {accessToken,refreshToken}
* `POST /auth/refresh` {refreshToken} → new tokens
* `POST /auth/logout` (revokes refresh)
* `POST /auth/password/forgot` | `/reset`

### Course Service

* `POST /courses` (INSTRUCTOR+) create
* `GET /courses?status=PUBLISHED&tag=java`
* `GET /courses/{id}`
* `PATCH /courses/{id}` (owners/instructors)
* `POST /courses/{id}/publish`
* `GET /courses/{id}/modules`

### Enrollment

* `POST /enrollments` {courseId,userId?} (self or admin)
* `GET /enrollments/me` (student view)
* `DELETE /enrollments/{id}` (drop)

### Assessment/Submission/Grading

* `POST /assessments` (INSTRUCTOR)
* `GET /assessments/{id}`
* `POST /assessments/{id}/start` (returns window + token)
* `POST /submissions` {assessmentId, answers}
* `POST /grading/auto/{submissionId}`
* `POST /grading/manual/{submissionId}` {breakdown, feedback}
* `POST /grading/{submissionId}/publish` → emits `GRADE_PUBLISHED`
* `GET /gradebook?courseId=...&userId=...`

### Analytics

* `GET /analytics/engagement?courseId=...` (DAU, completion rate)
* `GET /analytics/grade-distribution?courseId=...`
* `GET /analytics/funnels?courseId=...`

### Realtime (WebSocket)

* Connect `/ws` → subscribe to topics listed above.
* Server emits JSON payloads `{type, payload, timestamp}`.

> Every service exposes **Swagger** at `/swagger-ui.html` and OpenAPI JSON at `/v3/api-docs`.

---

## 6) Event Model (Kafka Topics)

* `user.created`, `user.role.changed`
* `course.published`, `course.archived`
* `enrollment.created`, `enrollment.dropped`
* `assessment.created`
* `submission.created`, `submission.graded`
* `grade.published`  ← triggers notifications + real-time push
* `content.uploaded`
* `analytics.signal` (generic engagement ping)

Event payloads include `tenantId`, `correlationId`, and minimal PII.

---

## 7) Business Rules (Hire-me polish)

* Draft courses cannot enroll students.
* Late submissions: configurable penalty in `assessment.settings`.
* Regrade requests open a **moderation workflow** (two-step approval for overrides).
* Instructors can **clone** a course (with/without student data).
* Plagiarism check is an interface; implement a stub that returns a similarity score for demo.
* Feature flags (per tenant) to toggle **Code Questions** and **Proctoring**.

---

## 8) Security & Compliance

* Passwords: Argon2id or BCrypt(12).
* JWT: RS256; rotate keys; JWKS endpoint published by auth-service; gateway validates.
* Refresh token rotation & reuse detection (revoke chain).
* Audit trail: write-ahead append collection `audit_logs`.
* Rate limit: IP + user-id bucket via gateway/Redis.
* CORS: only allowed frontends; strict headers.
* Data retention policies per tenant; soft delete with `deletedAt`.

---

## 9) Performance & Scale

* Read-heavy endpoints cached (Redis) with stampede protection.
* MongoDB: compound indexes

    * `courses(tenantId, status, tags)`
    * `enrollments(tenantId, userId, courseId)`
    * `submissions(tenantId, assessmentId, userId, status)`
* Pagination: cursor-based where feasible.
* Back-pressure on WebSocket broadcasts.

---

## 10) Testing Strategy

* **Unit**: 80%+ per service.
* **Integration**: Testcontainers for Mongo/Redis/Kafka.
* **Contract**: Spring Cloud Contract where services interact.
* **E2E smoke**: docker-compose up then run scripted cURL suite.
* **Security tests**: JWT tampering, RBAC denial paths.

---

## 11) Local Dev (Docker Compose)

`infra/docker-compose.yml` (Junie should generate full file):

* `mongodb` (with init scripts)
* `kafka` + `zookeeper`
* `redis`
* `minio`
* `otel-collector`, `prometheus`, `grafana`
* all services with hot reload (spring-devtools)

**Env example (`.env`):**

```
JWT_ISSUER=https://auth.edusync.local
JWT_PUBLIC_KEY_PATH=/keys/jwt.pub
JWT_PRIVATE_KEY_PATH=/keys/jwt.pem
MONGO_URI=mongodb://mongodb:27017
REDIS_URI=redis://redis:6379
KAFKA_BROKER=kafka:9092
S3_ENDPOINT=http://minio:9000
S3_ACCESS_KEY=edusync
S3_SECRET_KEY=edusyncsecret
```

**Run**

```
mvn clean verify -DskipTests=false
docker compose -f infra/docker-compose.yml up --build
```

---

## 12) Kubernetes (manifests Junie should emit)

* Namespace per environment.
* Deployments + Services per microservice.
* Ingress (NGINX) with TLS.
* ConfigMaps for configs; Secrets for creds and JWT keys.
* HorizontalPodAutoscaler on gateway, realtime, analytics.
* ServiceMonitor for Prometheus scraping.

---

## 13) Observability

* OpenTelemetry auto-instrumentation.
* Trace IDs in logs, propagated via headers.
* Grafana dashboards: request rate, error rate, p95 latency, WS connections, Kafka lag.
* Alert rules: high 5xx, abnormal grade publish latency, Kafka consumer lag.

---

## 14) Seed & Demo Data

* `scripts/seed.sh`:

    * Create tenant `acme-university`.
    * Create users: 1 admin, 2 instructors, 10 students.
    * Create 3 sample courses (Java Fundamentals, Data Structures, Spring Boot APIs).
    * Create 5 assessments per course; seed submissions for 50% of students.
    * Publish some grades to drive real-time events and analytics.

---

## 15) Sample cURL (Smoke)

```bash
# Register + login
curl -sX POST $GATEWAY/auth/register -H "X-Tenant-Id: acme" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!","firstName":"Alice","lastName":"Ngabo"}'

TOKENS=$(curl -sX POST $GATEWAY/auth/login -H "X-Tenant-Id: acme" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!"}')
ACCESS=$(echo $TOKENS | jq -r .accessToken)

# Create course (instructor)
curl -sX POST $GATEWAY/courses -H "Authorization: Bearer $ACCESS" -H "X-Tenant-Id: acme" \
  -H "Content-Type: application/json" -d '{"title":"Algorithms 101","code":"ALG101","status":"DRAFT"}'

# Publish
curl -sX POST $GATEWAY/courses/{id}/publish -H "Authorization: Bearer $ACCESS" -H "X-Tenant-Id: acme"

# Enroll self
curl -sX POST $GATEWAY/enrollments -H "Authorization: Bearer $ACCESS" -H "X-Tenant-Id: acme" \
  -H "Content-Type: application/json" -d '{"courseId":"..."}'
```

---

## 16) Code Conventions

* DTOs end with `Dto`; controllers lean; business in `*Service`.
* Validation with `jakarta.validation` annotations.
* Global exception handler returns RFC-7807 Problem JSON.
* MapStruct for mapping; Lombok for boilerplate.
* Use records where appropriate.

---

## 17) Nice-to-Have (Stretch)

* LTI 1.3 integration.
* Webhook subscriptions for external BI tools.
* Proctoring provider adapter interface.
* Code execution sandbox for programming questions (separate microservice + job queue).

---

## 18) Definition of Done (per feature)

* API documented (OpenAPI), unit + integration tests passing.
* RBAC enforced & audited.
* Metrics and traces visible.
* Seed data updated.
* Swagger examples added.
* Demo recorded scenario: publish course → enroll → submit → grade → real-time push → analytics chart updated.

---

## 19) Prompts for Junie AI (drop-in)

Use these as stepwise instructions:

1. **Scaffold Monorepo**

* Create Maven multi-module `edusync` with modules listed in section 3.
* Add Spring dependencies per service (web, security, data-mongodb, cloud gateway, websocket, kafka, validation, springdoc, cache, redis).

2. **Implement Auth**

* RS256 keypair generation.
* Endpoints from §5, token rotation, refresh store in Redis, blacklist on logout.
* JWKS endpoint, publish to gateway.

3. **Implement Gateway**

* JWT filter verifying JWKS; propagate `userId`, `roles`, `tenantId` as headers.
* Global rate limit (Redis), CORS, request/response logging.

4. **User & RBAC**

* Role + permission model, policy annotations like `@PreAuthorize("hasAuthority('course:publish')")`.

5. **Course/Enrollment**

* CRUD as per contracts; Mongo indexes; validation.
* Publish event `course.published`.

6. **Assessments/Submissions/Grading**

* Question model; auto-grading for MCQ/T/F; rubric scoring for essays.
* Emit `grade.published`; push via `notification-service` + `realtime-service`.

7. **Notifications**

* Email via SMTP container; in-app storage + `/me/notifications`.
* WebSocket channels; subscription auth.

8. **Analytics**

* Consumer of events; Mongo aggregation pipelines; endpoints from §5.

9. **Content**

* Signed URL upload/download via MinIO SDK; metadata stored in Mongo.

10. **Search**

* Index course title/description/tags; `/search?q=...`.

11. **Admin**

* Tenants CRUD, feature flags, quotas; audit logging.

12. **Infra & CI**

* Compose services, K8s manifests, GitHub Actions pipeline:

    * Build + test (with Testcontainers)
    * Docker build/push (tags: `sha`, `latest`)
    * Optional: deploy to K8s namespace on push to `main`.

13. **Seed & Smoke**

* Add `scripts/seed.sh` using curl to create demo data after `docker compose up`.

---

## 20) What Makes This Impressive (call-outs for your résumé)

* **True microservices** with **event-driven** edges (Kafka) and **real-time UX** (WebSocket).
* **Security-first**: RS256 JWT + refresh rotation + RBAC + audit trails.
* **Scalable**: Redis caching, HPA on K8s, idempotent consumers.
* **Data engineering flair**: analytics via Mongo aggregation + streaming sinks.
* **Polished DX**: Swagger everywhere, Testcontainers, one-command local spin-up.
* **Multi-tenant** architecture ready for SaaS.

---

## 21) Deliverables Junie Must Produce

* Source code for all services with tests (min 80% coverage).
* `infra/docker-compose.yml` & `infra/k8s/` manifests.
* `docs/openapi/*.yaml` aggregated at root.
* `README.md` with run/deploy instructions, diagrams, and demo screenshots.
* `scripts/seed.sh`, `scripts/create-tenant.sh`.
* Sample **Postman** collection.

---