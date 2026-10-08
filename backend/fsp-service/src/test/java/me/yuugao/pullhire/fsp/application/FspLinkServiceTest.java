package me.yuugao.pullhire.fsp.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import me.yuugao.pullhire.fsp.adapter.FspRegistryAdapter;
import me.yuugao.pullhire.fsp.adapter.RegistryAchievement;
import me.yuugao.pullhire.fsp.adapter.RegistryHistory;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Link/unlink use cases and their event emission rules ({@code docs/fsp_integration.md §3/§6}). */
class FspLinkServiceTest {

    private static final long CANDIDATE = 5L;
    private static final String FSP_ID = "fsp-1";

    private FspLinkRepository links;
    private AchievementCacheRepository achievements;
    private FspRegistryAdapter registry;
    private EventPublisher events;
    private FspLinkService service;

    @BeforeEach
    void setUp() {
        links = mock(FspLinkRepository.class);
        achievements = mock(AchievementCacheRepository.class);
        registry = mock(FspRegistryAdapter.class);
        events = mock(EventPublisher.class);
        service = new FspLinkService(links, achievements, registry, events);
    }

    @Test
    void firstLinkSyncsCacheAndPublishesBothEvents() {
        List<RegistryAchievement> items = List.of(
                new RegistryAchievement("a-1", "Победа", "championship", LocalDate.of(2023, 5, 14), 1, null));
        FspLink linked = link(FSP_ID, LinkStatus.LINKED, HistoryStatus.PRESENT);

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.empty(), Optional.of(linked));
        when(links.findByFspId(FSP_ID)).thenReturn(Optional.empty());
        when(registry.resolve(FSP_ID)).thenReturn(RegistryHistory.of(HistoryStatus.PRESENT, items));
        when(links.upsert(eq(CANDIDATE), eq(FSP_ID), eq(LinkStatus.LINKED), eq(HistoryStatus.PRESENT),
                any(Instant.class), any(Instant.class))).thenReturn(10L);

        FspLink result = service.link(CANDIDATE, FSP_ID);

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.LINKED);
        verify(links).upsert(eq(CANDIDATE), eq(FSP_ID), eq(LinkStatus.LINKED), eq(HistoryStatus.PRESENT),
                any(Instant.class), any(Instant.class));
        verify(events).publishLinked(CANDIDATE, FSP_ID, LinkStatus.LINKED, HistoryStatus.PRESENT);
        verify(events).publishAchievementsUpdated(CANDIDATE, FSP_ID, HistoryStatus.PRESENT, items);
    }

    @Test
    void repeatedLinkOfSameIdIsIdempotentAndSilent() {
        FspLink existing = link(FSP_ID, LinkStatus.LINKED, HistoryStatus.PRESENT);
        List<RegistryAchievement> items = List.of(
                new RegistryAchievement("a-1", "Победа", "championship", LocalDate.of(2023, 5, 14), 1, null));

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(existing), Optional.of(existing));
        when(links.findByFspId(FSP_ID)).thenReturn(Optional.of(existing));
        when(registry.resolve(FSP_ID)).thenReturn(RegistryHistory.of(HistoryStatus.PRESENT, items));
        when(achievements.listByLinkId(10L)).thenReturn(cached(items));
        when(links.upsert(eq(CANDIDATE), eq(FSP_ID), eq(LinkStatus.LINKED), eq(HistoryStatus.PRESENT),
                any(Instant.class), any(Instant.class))).thenReturn(10L);

        FspLink result = service.link(CANDIDATE, FSP_ID);

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.LINKED);
        verifyNoInteractions(events);
    }

    @Test
    void fspIdBoundToAnotherCandidateIsConflict() {
        FspLink other = new FspLink(2L, 99L, "dup", LinkStatus.LINKED, HistoryStatus.PRESENT,
                Instant.now(), Instant.now(), Instant.now(), Instant.now());

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.empty());
        when(links.findByFspId("dup")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.link(CANDIDATE, "dup"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status().value()).isEqualTo(409));
        verifyNoInteractions(registry, events);
    }

    @Test
    void adapterErrorMarksLinkErrorAndEmitsNoAchievementsEvent() {
        FspLink errored = link(FSP_ID, LinkStatus.ERROR, HistoryStatus.UNKNOWN);

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.empty(), Optional.of(errored));
        when(links.findByFspId(FSP_ID)).thenReturn(Optional.empty());
        when(registry.resolve(FSP_ID)).thenReturn(RegistryHistory.error());
        when(links.upsert(eq(CANDIDATE), eq(FSP_ID), eq(LinkStatus.ERROR), eq(HistoryStatus.UNKNOWN),
                any(Instant.class), any(Instant.class))).thenReturn(11L);

        FspLink result = service.link(CANDIDATE, FSP_ID);

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.ERROR);
        assertThat(result.historyStatus()).isEqualTo(HistoryStatus.UNKNOWN);
        verify(events).publishLinked(CANDIDATE, FSP_ID, LinkStatus.ERROR, HistoryStatus.UNKNOWN);
        verify(events, never()).publishAchievementsUpdated(anyLong(), any(), any(), any());
    }

    @Test
    void noHistoryIsNeutralLinkedStateWithoutAchievementsEvent() {
        FspLink linked = link("no_history", LinkStatus.LINKED, HistoryStatus.NO_HISTORY);

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.empty(), Optional.of(linked));
        when(links.findByFspId("no_history")).thenReturn(Optional.empty());
        when(registry.resolve("no_history")).thenReturn(RegistryHistory.of(HistoryStatus.NO_HISTORY, List.of()));
        when(links.upsert(eq(CANDIDATE), eq("no_history"), eq(LinkStatus.LINKED), eq(HistoryStatus.NO_HISTORY),
                any(Instant.class), any(Instant.class))).thenReturn(12L);

        FspLink result = service.link(CANDIDATE, "no_history");

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.LINKED);
        assertThat(result.historyStatus()).isEqualTo(HistoryStatus.NO_HISTORY);
        verify(events).publishLinked(CANDIDATE, "no_history", LinkStatus.LINKED, HistoryStatus.NO_HISTORY);
        verify(events, never()).publishAchievementsUpdated(anyLong(), any(), any(), any());
    }

    @Test
    void unlinkClearsCacheAndPublishesUnlinkedState() {
        FspLink linked = link(FSP_ID, LinkStatus.LINKED, HistoryStatus.PRESENT);
        FspLink unlinked = link(FSP_ID, LinkStatus.UNLINKED, HistoryStatus.UNKNOWN);

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(linked), Optional.of(unlinked));
        when(links.unlink(CANDIDATE)).thenReturn(1);

        FspLink result = service.unlink(CANDIDATE);

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.UNLINKED);
        verify(achievements).replaceByLinkId(10L, List.of());
        verify(events).publishLinked(CANDIDATE, FSP_ID, LinkStatus.UNLINKED, HistoryStatus.UNKNOWN);
        verify(events).publishAchievementsUpdated(CANDIDATE, FSP_ID, HistoryStatus.UNKNOWN, List.of());
    }

    @Test
    void adapterErrorOnExistingLinkKeepsLastGoodCacheAndHistory() {
        FspLink existing = link(FSP_ID, LinkStatus.LINKED, HistoryStatus.PRESENT);
        FspLink errored = link(FSP_ID, LinkStatus.ERROR, HistoryStatus.PRESENT);

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(existing), Optional.of(errored));
        when(links.findByFspId(FSP_ID)).thenReturn(Optional.of(existing));
        when(registry.resolve(FSP_ID)).thenReturn(RegistryHistory.error());
        when(links.upsert(eq(CANDIDATE), eq(FSP_ID), eq(LinkStatus.ERROR), eq(HistoryStatus.PRESENT),
                any(Instant.class), any(Instant.class))).thenReturn(10L);

        FspLink result = service.link(CANDIDATE, FSP_ID);

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.ERROR);
        assertThat(result.historyStatus()).isEqualTo(HistoryStatus.PRESENT);
        verifyNoInteractions(achievements);
        verify(events, never()).publishAchievementsUpdated(anyLong(), any(), any(), any());
    }

    @Test
    void reorderedAchievementsAreNotAChange() {
        FspLink existing = link(FSP_ID, LinkStatus.LINKED, HistoryStatus.PRESENT);
        RegistryAchievement first =
                new RegistryAchievement("a-1", "Победа", "championship", LocalDate.of(2023, 5, 14), 1, null);
        RegistryAchievement second =
                new RegistryAchievement("a-2", "Участие", "selection", LocalDate.of(2022, 9, 3), null, null);

        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.of(existing), Optional.of(existing));
        when(links.findByFspId(FSP_ID)).thenReturn(Optional.of(existing));
        when(registry.resolve(FSP_ID))
                .thenReturn(RegistryHistory.of(HistoryStatus.PRESENT, List.of(second, first)));
        when(achievements.listByLinkId(10L)).thenReturn(cached(List.of(first, second)));
        when(links.upsert(eq(CANDIDATE), eq(FSP_ID), eq(LinkStatus.LINKED), eq(HistoryStatus.PRESENT),
                any(Instant.class), any(Instant.class))).thenReturn(10L);

        service.link(CANDIDATE, FSP_ID);

        verifyNoInteractions(events);
    }

    @Test
    void unlinkWhenAbsentIsNoOp() {
        when(links.findByCandidateId(CANDIDATE)).thenReturn(Optional.empty());

        FspLink result = service.unlink(CANDIDATE);

        assertThat(result.linkStatus()).isEqualTo(LinkStatus.UNLINKED);
        verify(links, never()).unlink(anyLong());
        verifyNoInteractions(events);
    }

    private static FspLink link(String fspId, LinkStatus linkStatus, HistoryStatus historyStatus) {
        Instant now = Instant.now();
        return new FspLink(10L, CANDIDATE, fspId, linkStatus, historyStatus, now, now, now, now);
    }

    private static List<AchievementCache> cached(List<RegistryAchievement> items) {
        return items.stream()
                .map(item -> new AchievementCache(1L, 10L, item.externalAchievementId(), item.title(),
                        item.eventType(), item.achievedAt(), item.place(), item.payload(), Instant.now()))
                .toList();
    }
}
