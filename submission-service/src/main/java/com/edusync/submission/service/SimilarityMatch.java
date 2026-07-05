package com.edusync.submission.service;

/**
 * One candidate submission's similarity score against the target submission,
 * already rounded to 2 decimal places by SubmissionService#round2.
 */
public record SimilarityMatch(String submissionId, String userId, double similarityScore) {
}
