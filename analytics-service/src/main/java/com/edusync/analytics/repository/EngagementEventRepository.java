package com.edusync.analytics.repository;

import com.edusync.analytics.domain.EngagementEvent;
import com.edusync.analytics.domain.EngagementEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * WHY the DAU query is a hand-written {@code @Query} rather than a fully
 * derived method name: Spring Data JPA's derived-query keyword parser does
 * not reliably combine a {@code Distinct} modifier with the {@code count}
 * query subject across versions, and getting this subtly wrong would count
 * *rows* (repeat pings from the same user) instead of *distinct users* —
 * exactly the kind of silent correctness bug that would undermine the point
 * of replacing a hardcoded "dau": 42 with a real number. Writing the JPQL
 * explicitly removes that ambiguity.
 */
public interface EngagementEventRepository extends JpaRepository<EngagementEvent, String> {

    long countByCourseIdAndEventTypeAndOccurredAtAfter(String courseId, EngagementEventType eventType, Instant after);

    @Query("select count(distinct e.userId) from EngagementEvent e "
            + "where e.courseId = :courseId and e.eventType = :eventType and e.occurredAt > :after")
    long countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter(
            @Param("courseId") String courseId,
            @Param("eventType") EngagementEventType eventType,
            @Param("after") Instant after);
}
