package me.yuugao.pullhire.notification.ingest;

import me.yuugao.pullhire.notification.domain.NotificationLog;
import me.yuugao.pullhire.notification.domain.NotificationStatus;
import me.yuugao.pullhire.notification.mail.MailSenderAdapter;
import me.yuugao.pullhire.notification.mail.TemplateRenderer;
import me.yuugao.pullhire.notification.repo.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Status-aware dedup of {@link ContactReleasedListener}: first delivery
 * ({@code queued → sent}) sends exactly once; a redelivery with an existing
 * {@code sent} row sends nothing; an existing {@code failed} row is re-sent
 * in place (same id, no second row).
 */
class ContactReleasedListenerTest {

    private NotificationLogRepository logs;
    private TemplateRenderer renderer;
    private MailSenderAdapter mail;
    private ContactReleasedListener listener;

    @BeforeEach
    void setUp() {
        logs = mock(NotificationLogRepository.class);
        renderer = mock(TemplateRenderer.class);
        mail = mock(MailSenderAdapter.class);
        listener = new ContactReleasedListener(logs, renderer, mail);
    }

    private static ContactReleasedEvent event(String eventId) {
        return new ContactReleasedEvent(eventId, "candidate.contact_released", 1,
                "2026-10-08T10:00:00Z", "candidate-service", null,
                new ContactReleasedEvent.Payload("42", "contact@example.com",
                        "+70000000000", "2026-10-08T10:00:00Z"));
    }

    private static NotificationLog row(long id, String eventId, NotificationStatus status) {
        return new NotificationLog(id, 42L, "contact@example.com", "contact_released",
                "candidate.contact_released", eventId, status, null,
                status == NotificationStatus.FAILED ? "previous failure" : null,
                Instant.now(), status == NotificationStatus.SENT ? Instant.now() : null);
    }

    private void stubRender() {
        when(renderer.render(eq("contact_released"), eq("contact@example.com")))
                .thenReturn(new TemplateRenderer.RenderedEmail("subject", "body"));
    }

    @Test
    void newEventIsRenderedSentAndMarkedSent() {
        when(logs.findBySourceEventId("evt-1")).thenReturn(Optional.empty());
        when(logs.insertQueued(any())).thenReturn(10L);
        stubRender();

        listener.onContactReleased(event("evt-1"));

        verify(mail).send("contact@example.com", "subject", "body");
        verify(logs).markSent(eq(10L), eq(null), any());
        verify(logs, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void sentEventIdIsSkipped() {
        when(logs.findBySourceEventId("evt-1")).thenReturn(Optional.of(row(1L, "evt-1", NotificationStatus.SENT)));

        listener.onContactReleased(event("evt-1"));

        verify(logs, never()).insertQueued(any());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedPriorAttemptIsReusedAndReSent() {
        when(logs.findBySourceEventId("evt-1")).thenReturn(Optional.of(row(20L, "evt-1", NotificationStatus.FAILED)));
        stubRender();

        listener.onContactReleased(event("evt-1"));

        // The existing failed row is reused (no new insert) and retried to success.
        verify(logs, never()).insertQueued(any());
        verify(mail).send("contact@example.com", "subject", "body");
        verify(logs).markSent(eq(20L), eq(null), any());
    }
}
