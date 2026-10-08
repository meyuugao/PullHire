package me.yuugao.pullhire.notification.application;

import me.yuugao.pullhire.notification.domain.NotificationLog;
import me.yuugao.pullhire.notification.domain.NotificationStatus;
import me.yuugao.pullhire.notification.repo.NotificationLogRepository;
import me.yuugao.pullhire.notification.repo.PageResult;
import me.yuugao.pullhire.notification.web.dto.NotificationLogItem;
import me.yuugao.pullhire.notification.web.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pagination/mapping logic of {@link NotificationQueryService} against a mocked repo. */
class NotificationQueryServiceTest {

    private NotificationLogRepository repository;
    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationLogRepository.class);
        service = new NotificationQueryService(repository);
    }

    private static NotificationLog row(long id, long recipientUserId) {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        return new NotificationLog(id, recipientUserId, "user@example.com", "verify_email", "user.registered",
                "evt-" + id, NotificationStatus.SENT, null, null, now, now);
    }

    @Test
    void returnsPagedItemsForTheCallerOnly() {
        when(repository.findByRecipientUserId(7L, 0, 20, "created_at", false))
                .thenReturn(new PageResult<>(List.of(row(1, 7), row(2, 7)), 2L));

        PageResponse<NotificationLogItem> response = service.listOwn(7L, 0, 20, null);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).id()).isEqualTo("1");
        assertThat(response.items().get(0).recipientUserId()).isEqualTo("7");
        verify(repository).findByRecipientUserId(7L, 0, 20, "created_at", false);
    }

    @Test
    void clampsPageSizeAndNegativePage() {
        when(repository.findByRecipientUserId(7L, 0, 20, "created_at", false))
                .thenReturn(new PageResult<>(List.of(), 0L));
        when(repository.findByRecipientUserId(7L, 2, 100, "created_at", false))
                .thenReturn(new PageResult<>(List.of(), 0L));

        assertThat(service.listOwn(7L, -5, 0, null).size()).isEqualTo(20);
        assertThat(service.listOwn(7L, 2, 500, null).size()).isEqualTo(100);

        verify(repository).findByRecipientUserId(7L, 0, 20, "created_at", false);
        verify(repository).findByRecipientUserId(7L, 2, 100, "created_at", false);
    }

    @Test
    void parsesSortFieldAndDirectionAndRejectsUnknownColumn() {
        when(repository.findByRecipientUserId(7L, 0, 20, "status", true))
                .thenReturn(new PageResult<>(List.of(), 0L));
        when(repository.findByRecipientUserId(7L, 0, 20, "created_at", false))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.listOwn(7L, 0, 20, "status,asc");
        service.listOwn(7L, 0, 20, "not_a_column;drop");

        verify(repository).findByRecipientUserId(7L, 0, 20, "status", true);
        verify(repository).findByRecipientUserId(7L, 0, 20, "created_at", false);
    }
}
