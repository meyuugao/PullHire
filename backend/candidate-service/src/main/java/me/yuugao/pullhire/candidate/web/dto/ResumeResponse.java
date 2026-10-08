package me.yuugao.pullhire.candidate.web.dto;

import me.yuugao.pullhire.candidate.domain.Resume;

/** {@code POST /candidate/resume} response ({@code docs/api.md §3.2}). */
public record ResumeResponse(
        String id,
        String kind,
        String parseStatus,
        String fileName,
        Long sizeBytes) {

    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                Long.toString(resume.id()),
                resume.kind().wire(),
                resume.parseStatus().wire(),
                resume.fileName(),
                resume.sizeBytes());
    }
}
