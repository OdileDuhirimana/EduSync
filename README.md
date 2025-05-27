# EduSync LMS Backend (Portfolio Edition)

This repository contains a minimal scaffold of the EduSync LMS Backend, derived from the high-level blueprint in project.md. It is intentionally small to get you running fast, then increment incrementally toward the full architecture.

## What’s included (now)
- Maven multi-module monorepo with modules:
  - common (placeholder)
  - api-gateway (Spring Cloud Gateway, CORS, routes to services, request logging filter)
  - auth-service (register/login with BCrypt hashing, HS256 access token, /auth/health, Swagger)
  - user-service (stubbed profile endpoints: /users/health, /users/me)
  - course-service (stubbed CRUD: /courses with in-memory store + publish)
- Portfolio features (new):
  - `POST /analytics/study-plan`: personalized study schedule generation with due-date aware load balancing
  - `POST /analytics/at-risk`: learner risk scoring with recommendations
  - `POST /analytics/grade-forecast`: what-if grade simulation and target feasibility
  - `GET /submissions/{id}/similarity`: plagiarism/similarity radar for submissions in same assessment
  - Regrade workflow:
    - `POST /grading/regrade/{submissionId}/request`
    - `POST /grading/regrade/{requestId}/decision`
    - `GET /grading/regrade/{requestId}`
- Expanded tests for analytics intelligence, submission similarity, and regrade workflow
- Basic infra scaffolding (docker-compose with MongoDB + Redis)
- scripts/seed.sh registers a demo user via the gateway

## Prereqs
- Java 17+
- Maven 3.9+
- Docker (optional for compose)

## Build & Test
```
mvn -q -DskipTests=false clean verify
```

## Portfolio Feature Samples
```
GATEWAY=http://localhost:8080

# 1) Personalized study plan
curl -sX POST $GATEWAY/analytics/study-plan \
  -H "Content-Type: application/json" \
  -d '{
    "learnerId":"u-1",
    "weeklyHours":8,
    "horizonDays":7,
    "modules":[
      {"moduleId":"m1","title":"Recursion","estimatedMinutes":120,"difficulty":4,"dueDate":"2030-01-03"},
      {"moduleId":"m2","title":"Graphs","estimatedMinutes":90,"difficulty":5,"dueDate":"2030-01-05"}
    ]
  }' | jq .

# 2) At-risk detection
curl -sX POST $GATEWAY/analytics/at-risk \
  -H "Content-Type: application/json" \
  -d '{
    "courseId":"c-1",
    "learners":[{"userId":"u-risk","completionRate":0.2,"averageScore":45,"lastActiveDaysAgo":20,"missedDeadlines":4}]
  }' | jq .

# 3) Grade forecast
curl -sX POST $GATEWAY/analytics/grade-forecast \
  -H "Content-Type: application/json" \
  -d '{
    "learnerId":"u-1",
    "courseId":"c-1",
    "completed":[{"name":"Quiz 1","weightPct":30,"scorePct":80}],
    "remaining":[{"name":"Final","weightPct":70}],
    "targetFinalGrade":85
  }' | jq .
```

## Run locally (dev)
1. Start infrastructure (MongoDB, Redis) in another terminal (optional for now; auth in-memory users do not use Mongo yet):
```
docker compose -f infra/docker-compose.yml up -d
```
2. Start auth-service (port 9001). You can configure JWT via environment variables:
   - AUTH_JWT_SECRET: secret string (raw or base64). Default is a dev value.
   - AUTH_JWT_ACCESS_TTL_SECONDS: access token TTL (default 900 sec)
```
cd auth-service
export AUTH_JWT_SECRET="dev-secret-please-change" # optional
mvn spring-boot:run
```
3. Start user-service (port 9002):
```
cd ../user-service && mvn spring-boot:run
```
4. Start course-service (port 9003):
```
cd ../course-service && mvn spring-boot:run
```
5. Start api-gateway (port 8080):
```
cd ../api-gateway && mvn spring-boot:run
```

## Smoke (manual)
```
GATEWAY=http://localhost:8080
curl -sX GET $GATEWAY/actuator/health | jq .

# Register
curl -sX POST $GATEWAY/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!","firstName":"Alice","lastName":"Ngabo"}' | jq .

# Login (stubbed tokens)
curl -sX POST $GATEWAY/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!"}' | jq .
```

## Automated Smoke (script)
After starting the services (see Run locally), you can run a scripted smoke that hits core APIs end-to-end via the gateway.
```
# In repo root
chmod +x scripts/smoke.sh
GATEWAY_URL=http://localhost:8080 TENANT=acme EMAIL=alice@acme.edu PASSWORD='P@ssw0rd!' ./scripts/smoke.sh
```

## Docker Compose (optional)
A minimal compose file is provided under infra/docker-compose.yml with MongoDB and Redis (future use) and comments for services. Build images or run services directly as shown above.

## Clean Git History Strategy
This directory currently has no `.git` metadata. To produce clean portfolio history from this state:
```
cd /home/odiboo/Portfolio/EduSync
git init
git add .
git commit -m "chore: bootstrap EduSync portfolio baseline"
```

Then keep history clean with one concern per commit:
1. `feat(analytics): add study planner, risk scoring, and grade forecast`
2. `feat(submission): add similarity/plagiarism radar`
3. `feat(grading): add regrade moderation workflow`
4. `test: add feature-level API tests`
5. `docs: update README with portfolio features and runbook`

## Next Steps (roadmap short)
- Auth: implement real JWT (RS256), refresh rotation (Redis), Mongo persistence, JWKS.
- Gateway: JWT verification from JWKS, propagate user headers, rate limiting (Redis).
- Add user-service and course-service with Mongo models and OpenAPI.
- Add Testcontainers for Mongo/Redis and integration tests.
- Expand docker-compose to include Kafka, MinIO, and services.
- K8s manifests and GitHub Actions CI.

Refer to project.md for the full blueprint.


## Additional Smoke (via Gateway)
```
GATEWAY=http://localhost:8080

# Health checks
curl -s $GATEWAY/auth/health | jq .
curl -s $GATEWAY/users/health | jq .
curl -s $GATEWAY/courses/health | jq .

# Register + login (same as before)
curl -sX POST $GATEWAY/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@acme.edu","password":"P@ssw0rd!","firstName":"Alice","lastName":"Ngabo"}' | jq .
TOKENS=$(curl -sX POST $GATEWAY/auth/login -H "Content-Type: application/json" -d '{"email":"alice@acme.edu","password":"P@ssw0rd!"}')
ACCESS=$(echo $TOKENS | jq -r .accessToken)

# Call user profile endpoints (headers stub for demo)
curl -s $GATEWAY/users/me -H "X-User-Id: u-123" -H "X-User-Email: alice@acme.edu" | jq .

# Create a course (requires INSTRUCTOR role header for now)
CREATE=$(curl -sX POST $GATEWAY/courses \
  -H "Content-Type: application/json" \
  -H "X-User-Roles: INSTRUCTOR" \
  -d '{"code":"ALG101","title":"Algorithms 101"}')
echo $CREATE | jq .
COURSE_ID=$(echo $CREATE | jq -r .id)

# Publish the course
curl -sX POST $GATEWAY/courses/$COURSE_ID/publish -H "X-User-Roles: INSTRUCTOR" | jq .
```
