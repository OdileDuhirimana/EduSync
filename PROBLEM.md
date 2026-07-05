# Problem Validation

This document exists specifically to close a gap an external portfolio audit
found: the README and `project.md` described *what* EduSync is (a
microservices LMS backend) and *how* it's built, but never *why* it should
exist — no pain-point narrative, no named users, no success criteria, no
comparison to existing products. This is that missing context, written
honestly for a portfolio-stage backend system rather than as marketing copy
for a funded startup.

## 1. Problem Statement

Small and mid-size course providers — coding bootcamps, university
departments running their own tooling, corporate L&D teams, independent
cohort-based course creators — are stuck between two bad options:

1. **Adopt a full LMS platform** (Canvas, Moodle, Blackboard) built for
   institutions with dedicated IT staff, procurement cycles, and hundreds of
   configuration screens they will never use. These platforms are correct
   choices at university scale and are not being "disrupted" here — they are
   simply the wrong weight class for a 50-person bootcamp cohort or a
   20-person corporate training program.
2. **Duct-tape a workflow together** out of a spreadsheet for grades, a
   generic form tool for assignment submission, a chat app for
   announcements, and manual copy-pasting between them. This is what
   actually happens at this scale today, and it fails in predictable ways:
   grade calculations drift out of sync across spreadsheet copies, there is
   no per-student audit trail for a grade dispute, plagiarism checking is
   either skipped entirely or done by eye, and no one can answer "which
   students are falling behind right now" without manually cross-referencing
   three tools.

EduSync targets the specific, narrow gap between those two options: a
small set of the LMS capabilities that actually cause pain at this scale —
enrollment with real integrity guarantees, submission handling with
built-in similarity detection, a real grade/regrade audit trail, and
at-risk-learner signals — without the operational weight of a full
institutional platform.

## 2. Target Users

**Primary persona — "Priya, Bootcamp Lead Instructor."** Runs a 30-40
student cohort-based technical bootcamp. Needs to: publish course modules,
receive and grade submissions, run a lightweight plagiarism check before
grading, and see which students need intervention before they fail out
silently. Has no dedicated backend/DevOps support — whatever she uses has
to run reliably with minimal operational babysitting.

**Secondary persona — "Daniel, Student."** Enrolled in 1-3 concurrent
courses. Needs to: see his own enrollments, submit assessments before a
deadline, see his grade and request a regrade if he believes a grading
error occurred, and get a realistic answer to "what do I need to score on
the final to hit my target grade."

**Tertiary persona — "Amara, Program Administrator/Registrar."** Oversees
multiple instructors and cohorts. Needs the ADMIN-level override capability
this system's `Role` model already provides: publish/unpublish any course
regardless of which instructor authored it, decide contested regrade cases,
and see aggregate engagement/completion data across the whole program, not
just one instructor's course.

These three roles map directly to the `STUDENT` / `INSTRUCTOR` / `ADMIN`
enum already implemented in `common`'s `Role` type and enforced via
`CallerContext.requireRole(...)` throughout every remediated service — the
persona definitions here are not aspirational, they describe the access
model that is actually running in the code.

## 3. Success Metrics

Honest, backend-appropriate metrics for a system at this stage (no invented
user-growth numbers for a project with no live users):

- **Correctness under the system's own test suite**: 100% of critical
  authorization paths (role-gated create/publish/grade-override/regrade-
  decision endpoints) covered by an automated test that proves the
  unauthorized path is actually rejected, not just that the authorized path
  works. Measured today: yes, for every remediated service — see each
  service's `*ControllerTest` for its 403/401 cases.
- **Data integrity**: zero orphaned enrollments (an enrollment referencing a
  course that doesn't exist or isn't published). Enforced today via
  enrollment-service's real-time call to course-service before persisting
  an enrollment (see `EnrollmentService#create`), not just aspirationally
  documented.
- **Coverage floor, CI-enforced, not just claimed**: JaCoCo's `check` goal
  fails the build below a documented line-coverage threshold (see root
  `pom.xml`) — this makes "we have good test coverage" a falsifiable,
  automatically-checked claim rather than a README assertion.
- **Time-to-detect an at-risk learner**: the `at-risk` analytics endpoint
  turns raw per-student signals (completion rate, average score, inactivity,
  missed deadlines) into a same-request risk score and recommendation —
  the metric this is meant to eventually move, in a real deployment, is
  "days between a student falling behind and an instructor noticing,"
  which today in the spreadsheet-and-vibes status quo is often "never."

## 4. Competitive Differentiation

| | Canvas / Blackboard | Moodle | Google Classroom | EduSync (this project) |
|---|---|---|---|---|
| Target scale | Institution-wide, thousands of students | Institution-wide, self-hosted | Any size, free | Single cohort / small program (tens to low hundreds of students) |
| Setup burden | Procurement + IT admin required | Self-hosted server, plugin ecosystem to manage | Near-zero (Google account) | A handful of Docker containers behind one gateway |
| Built-in plagiarism detection | Via paid Turnitin integration | Via paid plugin | None | Built in (`submission-service`'s Jaccard-similarity check), no third-party integration or extra cost |
| Regrade dispute workflow | Manual, instructor email/gradebook comments | Manual | None | A real, typed, auditable state machine (`PENDING` → `APPROVED`/`REJECTED`) in `grading-service` |
| At-risk learner scoring | Add-on analytics modules (extra cost/config) | Add-on plugin | None | Built in, computed per request from raw signals |
| Deployment model | Vendor-hosted / heavy self-host | Self-hosted, single monolith | Vendor-hosted (Google), no self-host option | Self-hostable microservices, runs locally with zero external infrastructure (H2 file-mode, no managed DB required to start) |

**Where EduSync deliberately does not compete**: multi-institution
scale, a rich frontend/UI (this is a backend-only portfolio project — see
README), third-party integrations (SIS, payment, video conferencing), and
long-tail LMS features (discussion forums, peer review, badges/certificates).
The bet this project makes is that a small, correctly-modeled subset of LMS
capability — with real authorization, real persistence, and real data
integrity — is more valuable to demonstrate at a portfolio/interview stage
than a shallow clone of every Canvas menu item.
