package me.yuugao.pullhire.fsp.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.fsp.domain.AchievementCache;
import me.yuugao.pullhire.fsp.domain.FspLink;
import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import me.yuugao.pullhire.fsp.domain.LinkStatus;
import me.yuugao.pullhire.fsp.error.ApiException;
import me.yuugao.pullhire.fsp.repo.AchievementCacheRepository;
import me.yuugao.pullhire.fsp.repo.FspLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read side: unlinked → 404, linked/no_history → neutral 200 read. */
class FspQueryServiceTest {

    private static final long CANDIDATE = 5L;

    private FspLinkRepository links;
    private AchievementCacheRepository achievements;
    private FspQueryService query;

    @BeforeEach
    void setUp() {
        links = mock(FspLinkRepository.class);
        achievements = mock(AchievementCacheRepository.class);
        query = new FspQueryService(links, achievements);
    }

    @Test
    void absentLinkIsNotFound() {
        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.requireLinked(CANDIDATE))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(404));
    }

    @Test
    void unlinkedRowIsTreatedAsNoLink() {
        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(
                new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.UNLINKED, HistoryStatus.UNKNOWN,
                        null, null, Instant.now(), Instant.now())));

        assertThat(query.findLinked(CANDIDATE)).isEmpty();
    }

    @Test
    void noHistoryLinkReturnsEmptyAchievementsWithoutError() {
        FspLink link = new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.LINKED, HistoryStatus.NO_HISTORY,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(link));
        when(achievements.listByLinkId(10L)).thenReturn(List.of());

        FspLink found = query.requireLinked(CANDIDATE);

        assertThat(found.historyStatus()).isEqualTo(HistoryStatus.NO_HISTORY);
        assertThat(query.achievements(found.id())).isEmpty();
    }

    @Test
    void presentLinkReturnsCachedItems() {
        FspLink link = new FspLink(10L, CANDIDATE, "fsp-1", LinkStatus.LINKED, HistoryStatus.PRESENT,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());
        AchievementCache item = new AchievementCache(1L, 10L, "a-1", "Победа", "championship",
                null, 1, null, Instant.now());
        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(link));
        when(achievements.listByLinkId(10L)).thenReturn(List.of(item));

        FspLink found = query.requireLinked(CANDIDATE);

        assertThat(query.achievements(found.id())).containsExactly(item);
    }
}
