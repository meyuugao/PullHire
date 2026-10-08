package me.yuugao.pullhire.assessment.application;

import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.repo.AttemptRepository;
import me.yuugao.pullhire.assessment.repo.GradeHistoryRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.HistoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only candidate history ({@code docs/api.md §3.4}): the candidate's
 * sessions newest-first, each with its attempts in serving order, plus the
 * append-only grade-change journal oldest-first. No writes and no dedicated
 * candidate-level attempt query exist, so attempts are composed in application
 * code by iterating {@link TestSessionRepository#findByCandidate(long)} and
 * {@link AttemptRepository#findBySession(long)}.
 */
@Service
public class HistoryService {

    private final TestSessionRepository sessions;
    private final AttemptRepository attempts;
    private final GradeHistoryRepository gradeHistory;

    public HistoryService(TestSessionRepository sessions, AttemptRepository attempts,
                          GradeHistoryRepository gradeHistory) {
        this.sessions = sessions;
        this.attempts = attempts;
        this.gradeHistory = gradeHistory;
    }

    /**
     * Full read-only history of one candidate. Ordering follows the repositories:
     * sessions newest-first by {@code started_at}, attempts in serving order, and
     * grade history oldest-first by {@code created_at}.
     */
    @Transactional(readOnly = true)
    public HistoryResponse history(long candidateId) {
        List<HistoryResponse.SessionHistory> sessionHistory = new ArrayList<>();
        for (TestSession session : sessions.findByCandidate(candidateId)) {
            List<HistoryResponse.AttemptItem> items = attempts.findBySession(session.id()).stream()
                    .map(HistoryResponse.AttemptItem::from)
                    .toList();
            sessionHistory.add(HistoryResponse.SessionHistory.from(session, items));
        }
        List<HistoryResponse.GradeHistoryItem> grades = gradeHistory.listByCandidate(candidateId).stream()
                .map(HistoryResponse.GradeHistoryItem::from)
                .toList();
        return new HistoryResponse(List.copyOf(sessionHistory), grades);
    }
}
