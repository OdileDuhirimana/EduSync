package com.edusync.submission.api.dto;

import com.edusync.submission.service.SimilarityMatch;
import com.edusync.submission.service.SimilarityResult;

import java.util.List;

public record SimilarityResponseDto(
        String submissionId,
        String assessmentId,
        String riskLevel,
        double maxSimilarity,
        int comparedSubmissions,
        List<MatchDto> matches
) {
    public static SimilarityResponseDto from(SimilarityResult result) {
        return new SimilarityResponseDto(
                result.submissionId(),
                result.assessmentId(),
                result.riskLevel(),
                result.maxSimilarity(),
                result.comparedSubmissions(),
                result.matches().stream().map(MatchDto::from).toList());
    }

    public record MatchDto(String submissionId, String userId, double similarityScore) {
        public static MatchDto from(SimilarityMatch match) {
            return new MatchDto(match.submissionId(), match.userId(), match.similarityScore());
        }
    }
}
