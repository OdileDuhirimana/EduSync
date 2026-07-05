package com.edusync.submission.service;

import java.util.List;

/**
 * Outcome of SubmissionService#computeSimilarity: a plain service-layer
 * result type (not an api/dto class) so the service package has no
 * dependency on the web layer, keeping dependencies flowing inward.
 * SimilarityResponseDto#from(SimilarityResult) maps this to the HTTP
 * response shape in the api layer.
 */
public record SimilarityResult(
        String submissionId,
        String assessmentId,
        String riskLevel,
        double maxSimilarity,
        int comparedSubmissions,
        List<SimilarityMatch> matches
) {
}
