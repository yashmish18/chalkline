package com.iet.dashboard.submissions.web;

import com.iet.dashboard.submissions.domain.SubmissionFile;

import java.time.Instant;

public record SubmissionFileDto(
        Long id,
        Long submissionId,
        int version,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt
) {
    public static SubmissionFileDto fromEntity(SubmissionFile file) {
        return new SubmissionFileDto(
                file.getId(),
                file.getSubmission().getId(),
                file.getVersion(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getUploadedAt()
        );
    }
}
