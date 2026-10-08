package me.yuugao.pullhire.candidate.web.dto;

/**
 * {@code GET /internal/candidates/by-user/{userId}} response ({@code docs/api.md §3.2}):
 * the identity mapping other services need — the caller's JWT {@code sub}
 * ({@code auth.users.id}) to the logical candidate id ({@code candidate.profiles.id},
 * {@code docs/data_model.md §4.1}). Carries no PII.
 */
public record InternalCandidateResponse(long candidateId) {
}
