package com.edusync.enrollment.client;

import com.edusync.common.error.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Synchronous HTTP implementation of {@link CourseClient}, backed by Spring's
 * {@code RestClient} (available since Spring Framework 6.1 / Boot 3.2 — no
 * extra dependency needed beyond {@code spring-boot-starter-web}, which
 * enrollment-service already depends on). A synchronous client is the
 * correct choice here because enrollment-service itself is a classic Spring
 * MVC (servlet) application, not a WebFlux one — mixing in a reactive
 * WebClient would add a second concurrency model for no benefit.
 *
 * WHY fail closed on any transport error (closes ERR-04's "no inter-service
 * network-failure handling" for this specific call path): if course-service
 * is unreachable, this throws {@link ServiceUnavailableException} rather than
 * silently treating the course as valid — an enrollment integrity check that
 * can be bypassed by taking the dependency down would not be a real check.
 * A full circuit breaker / retry-with-backoff (Resilience4j) is a documented
 * follow-up (see README "Known Limitations"), not implemented here to stay
 * within this project's pre-approved dependency list; the bounded connect/read
 * timeouts configured on the injected {@link RestClient} (see
 * CourseClientConfig) are what prevent a hung course-service from blocking an
 * enrollment request indefinitely in the meantime.
 */
@Component
public class RestClientCourseClient implements CourseClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientCourseClient.class);

    private final RestClient restClient;

    public RestClientCourseClient(RestClient courseServiceRestClient) {
        this.restClient = courseServiceRestClient;
    }

    @Override
    public Optional<CourseSummary> findById(String courseId) {
        try {
            CourseSummary course = restClient.get()
                    .uri("/courses/{id}", courseId)
                    .retrieve()
                    .body(CourseSummary.class);
            return Optional.ofNullable(course);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientException transportOrServerError) {
            log.warn("course-service call failed for courseId={}: {}", courseId, transportOrServerError.getMessage());
            throw new ServiceUnavailableException(
                    "Could not verify course '" + courseId + "' with course-service right now");
        }
    }
}
