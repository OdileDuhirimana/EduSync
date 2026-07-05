package com.edusync.enrollment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * WHY explicit, short timeouts (not the client library's defaults, which are
 * effectively "wait forever" for connect and infinite for read on the plain
 * JDK client): the whole point of {@link com.edusync.enrollment.client.CourseClient}
 * is to fail fast and closed when course-service is unreachable (see
 * RestClientCourseClient's Javadoc) — an unbounded timeout would let a single
 * hung dependency exhaust enrollment-service's own request-handling threads
 * under load, turning one slow downstream into a full outage of this
 * service too.
 */
@Configuration
public class CourseClientConfig {

    @Bean
    public RestClient courseServiceRestClient(
            @Value("${services.course.base-url:http://localhost:9003}") String courseServiceBaseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        return RestClient.builder()
                .baseUrl(courseServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
