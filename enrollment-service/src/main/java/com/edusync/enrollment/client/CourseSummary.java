package com.edusync.enrollment.client;

/**
 * enrollment-service's own view of a course, deserialized from
 * course-service's {@code GET /courses/{id}} response.
 *
 * WHY a locally-defined record instead of a shared/compiled dependency on
 * course-service's DTOs: these are two independently deployable
 * microservices communicating over HTTP, not two modules sharing a compile
 * artifact. Coupling enrollment-service's build to course-service's DTO
 * classes would reintroduce exactly the tight coupling a service boundary is
 * supposed to prevent — if course-service's response ever gains fields
 * enrollment-service doesn't care about, Jackson's default deserialization
 * simply ignores them here.
 */
public record CourseSummary(String id, String status) {

    public boolean isPublished() {
        return "PUBLISHED".equalsIgnoreCase(status);
    }
}
