package com.edusync.enrollment.client;

import java.util.Optional;

/**
 * WHY this interface exists at all rather than calling {@code RestClient}
 * directly from {@code EnrollmentService} (closes DB-02 / Critical Risk 4 in
 * the portfolio evaluation: "no cross-service referential integrity...
 * enrollment-service never verifies a courseId exists, is published, or has
 * capacity — zero inter-service HTTP calls exist anywhere in the codebase"):
 * depending on this abstraction (Dependency Inversion) rather than a concrete
 * HTTP client is what lets {@code EnrollmentServiceTest} substitute a fast,
 * deterministic Mockito mock instead of requiring a real, running
 * course-service instance for every unit test — exactly the same reasoning
 * that motivated {@code EnrollmentRepository} being an interface in the
 * first place.
 */
public interface CourseClient {

    /**
     * @return the course if course-service reports it exists, or
     * {@link Optional#empty()} if course-service responded with 404.
     * @throws com.edusync.common.error.ServiceUnavailableException if
     * course-service could not be reached at all (timeout, connection
     * refused, or an unexpected non-2xx/404 response) — a transport failure,
     * not a business answer.
     */
    Optional<CourseSummary> findById(String courseId);
}
