package com.edusync.analytics.repository;

import com.edusync.analytics.domain.FunnelEvent;
import com.edusync.analytics.domain.FunnelStage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunnelEventRepository extends JpaRepository<FunnelEvent, String> {

    long countByCourseIdAndStage(String courseId, FunnelStage stage);
}
