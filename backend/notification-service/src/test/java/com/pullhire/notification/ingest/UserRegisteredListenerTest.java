package com.pullhire.notification.ingest;

import com.pullhire.notification.domain.NotificationLog;
import com.pullhire.notification.domain.NotificationStatus;
import com.pullhire.notification.mail.MailSenderAdapter;
import com.pullhire.notification.mail.TemplateRenderer;
import com.pullhire.notification.repo.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Status-aware dedup / happy-path / failure transitions of
 * {@link UserRegisteredListener}: only a terminal {@code sent} row is skipped; a
 * {@code failed}/{@code queued} row is reused and re-sent so bounded retry works
 * and exhausted retries reach the DLQ.
 */
class UserRegisteredListenerTest {

    private static final String VERIFICATION_URL =
            "https://app.pullhire.local/verify-email?token=raw-token";

    private NotificationLogRepository logs;
    private TemplateRenderer renderer;
    private MailSenderAdapter mail;
    private UserRegisteredListener listener;

    @BeforeEach
    void setUp() {
        logs = mock(NotificationLogRepository.class);
        renderer = mock(TemplateRenderer.class);
        mail = mock(MailSenderAdapter.class);
        listener = new UserRegisteredListener(logs, renderer, mail);
    }

    private static UserRegisteredEvent event(String eventId) {
        return new UserRegisteredEvent(eventId, "user.registered", 1, "2026-10-07T10:00:00Z", "auth-service", null,
                new UserRegisteredEvent.Payload("42", "user@example.com", "candidate", VERIFICATION_URL));
    }

    private static NotificationLog row(long id, NotificationStatus status) {
        return new NotificationLog(id, 42L, "user@example.com", "verify_email", "user.registered",
                "evt-x", status, null, status == NotificationStatus.FAILED ? "previous failure" : null,
                Instant.now(), status == NotificationStatus.SENT ? Instant.now() : null);
    }

    private void stubRender() {
        when(renderer.render(eq("verify_email"), eq("user@example.com"), eq(VERIFICATION_URL)))
                .thenReturn(new TemplateRenderer.RenderedEmail("subject", "body"));
    }

    @Test
    void sentEventIdIsSkipped() {
        when(logs.findBySourceEventId("evt-1")).thenReturn(Optional.of(row(1L, NotificationStatus.SENT)));

        listener.onUserRegistered(event("evt-1"));

        verify(logs, never()).insertQueued(any());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void newEventIsRenderedSentAndMarkedSent() {
        when(logs.findBySourceEventId("evt-2")).thenReturn(Optional.empty());
        when(logs.insertQueued(any())).thenReturn(10L);
        stubRender();

        listener.onUserRegistered(event("evt-2"));

        verify(mail).send("user@example.com", "subject", "body");
        verify(logs).markSent(eq(10L), eq(null), any());
        verify(logs, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void failedPriorAttemptIsReusedAndReSent() {
        when(logs.findBySourceEventId("evt-4")).thenReturn(Optional.of(row(20L, NotificationStatus.FAILED)));
        stubRender();

        listener.onUserRegistered(event("evt-4"));

        // The existing failed row is reused (no new insert) and retried to success.
        verify(logs, never()).insertQueued(any());
        verify(mail).send("user@example.com", "subject", "body");
        verify(logs).markSent(eq(20L), eq(null), any());
    }

    @Test
    void queuedPriorAttemptIsReusedAndReSent() {
        when(logs.findBySourceEventId("evt-5")).thenReturn(Optional.of(row(21L, NotificationStatus.QUEUED)));
        stubRender();

        listener.onUserRegistered(event("evt-5"));

        verify(logs, never()).insertQueued(any());
        verify(logs).markSent(eq(21L), eq(null), any());
    }

    @Test
    void sendFailureIsRecordedAndPropagatesToRetryPath() {
        when(logs.findBySourceEventId("evt-3")).thenReturn(Optional.empty());
        when(logs.insertQueued(any())).thenReturn(11L);
        stubRender();
        doThrow(new RuntimeException("smtp down")).when(mail).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> listener.onUserRegistered(event("evt-3")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");

        verify(logs).markFailed(11L, "smtp down");
        verify(logs, never()).markSent(anyLong(), any(), any());
    }

    @Test
    void repeatedFailureKeepsReusingTheFailedRowUntilExhausted() {
        // First delivery inserted+failed (row 30); the retry sees the failed row
        // and re-attempts — and fails again, still on the same row.
        when(logs.findBySourceEventId("evt-6")).thenReturn(Optional.of(row(30L, NotificationStatus.FAILED)));
        stubRender();
        doThrow(new RuntimeException("smtp down again")).when(mail).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> listener.onUserRegistered(event("evt-6")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down again");

        verify(logs, never()).insertQueued(any());
        verify(logs).markFailed(30L, "smtp down again");
    }

    @Test
    void concurrentDuplicateRereadsFailedRowAndRetries() {
        when(logs.findBySourceEventId("evt-7"))
                .thenReturn(Optional.empty(), Optional.of(row(40L, NotificationStatus.FAILED)));
        when(logs.insertQueued(any())).thenThrow(new DuplicateKeyException("source_event_id"));
        stubRender();

        listener.onUserRegistered(event("evt-7"));

        verify(mail).send("user@example.com", "subject", "body");
        verify(logs).markSent(eq(40L), eq(null), any());
    }

    @Test
    void concurrentDuplicateRereadsSentRowAndSkips() {
        when(logs.findBySourceEventId("evt-8"))
                .thenReturn(Optional.empty(), Optional.of(row(41L, NotificationStatus.SENT)));
        when(logs.insertQueued(any())).thenThrow(new DuplicateKeyException("source_event_id"));

        listener.onUserRegistered(event("evt-8"));

        verify(mail, never()).send(anyString(), anyString(), anyString());
        verify(logs, never()).markSent(anyLong(), any(), any());
    }
}
