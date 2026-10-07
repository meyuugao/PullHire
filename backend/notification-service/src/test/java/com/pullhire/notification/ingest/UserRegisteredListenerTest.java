package com.pullhire.notification.ingest;

import com.pullhire.notification.mail.MailSenderAdapter;
import com.pullhire.notification.mail.TemplateRenderer;
import com.pullhire.notification.repo.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dedup / happy-path / failure transitions of {@link UserRegisteredListener}. */
class UserRegisteredListenerTest {

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
                new UserRegisteredEvent.Payload("42", "user@example.com", "candidate", "raw-token"));
    }

    @Test
    void duplicateEventIdIsSkipped() {
        when(logs.existsBySourceEventId("evt-1")).thenReturn(true);

        listener.onUserRegistered(event("evt-1"));

        verify(logs, never()).insertQueued(any());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void newEventIsRenderedSentAndMarkedSent() {
        when(logs.existsBySourceEventId("evt-2")).thenReturn(false);
        when(logs.insertQueued(any())).thenReturn(10L);
        when(renderer.render(eq("verify_email"), eq("user@example.com"), eq("raw-token")))
                .thenReturn(new TemplateRenderer.RenderedEmail("subject", "body"));

        listener.onUserRegistered(event("evt-2"));

        verify(mail).send("user@example.com", "subject", "body");
        verify(logs).markSent(eq(10L), eq(null), any());
        verify(logs, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void sendFailureMarksRowFailed() {
        when(logs.existsBySourceEventId("evt-3")).thenReturn(false);
        when(logs.insertQueued(any())).thenReturn(11L);
        when(renderer.render(anyString(), anyString(), anyString()))
                .thenReturn(new TemplateRenderer.RenderedEmail("subject", "body"));
        doThrow(new RuntimeException("smtp down")).when(mail).send(anyString(), anyString(), anyString());

        listener.onUserRegistered(event("evt-3"));

        verify(logs).markFailed(11L, "smtp down");
        verify(logs, never()).markSent(anyLong(), any(), any());
    }
}
