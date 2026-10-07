package com.pullhire.candidate.domain;

import java.time.Instant;

/**
 * Row of {@code candidate.resumes} ({@code docs/data_model.md §4.2}). Metadata
 * only; the PDF bytes live in the resume volume at {@code filePath}.
 */
public record Resume(
        long id,
        long profileId,
        ResumeKind kind,
        String fileName,
        String filePath,
        String mimeType,
        Long sizeBytes,
        ParseStatus parseStatus,
        Instant parsedAt,
        Instant createdAt) {
}
