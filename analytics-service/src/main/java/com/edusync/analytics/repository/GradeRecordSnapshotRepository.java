package com.edusync.analytics.repository;

import com.edusync.analytics.domain.GradeRecordSnapshot;
import com.edusync.analytics.domain.LetterGrade;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * WHY one derived count-per-grade method instead of a single GROUP BY query:
 * AnalyticsService#gradeDistribution calls this once per {@link LetterGrade}
 * enum constant (five calls total), which keeps the result shape trivially
 * correct (every grade bucket always appears, even at zero) without needing
 * to post-process a sparse GROUP BY result set that could be missing rows
 * for grades nobody has received yet.
 */
public interface GradeRecordSnapshotRepository extends JpaRepository<GradeRecordSnapshot, String> {

    long countByCourseIdAndLetterGrade(String courseId, LetterGrade letterGrade);
}
