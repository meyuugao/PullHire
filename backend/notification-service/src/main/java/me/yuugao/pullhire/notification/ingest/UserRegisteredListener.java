package me.yuugao.pullhire.notification.ingest;

import me.yuugao.pullhire.notification.config.AmqpConfig;
import me.yuugao.pullhire.notification.domain.NotificationLog;
import me.yuugao.pullhire.notification.domain.NotificationStatus;
import me.yuugao.pullhire.notification.mail.MailSenderAdapter;
import me.yuugao.pullhire.notification.mail.TemplateRenderer;
import me.yuugao.pullhire.notification.repo.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumer of {@code user.registered} on {@code notification.user-registered}
 * ({@code docs/architecture.md §4.2}). Dedups by {@code event_id} against the
 * UNIQUE {@code notification_log.source_event_id}, renders the {@code verify_email}
 * template, sends via SMTP, and records the {@code queued → sent/failed} transition.
 *
 * <p><b>Status-aware dedup.</b> An event is skipped only when the existing row is
 * in the terminal-success state {@link NotificationStatus#SENT}. A {@code failed}
 * (or still-{@code queued}) row is a retryable prior attempt: its id is reused and
 * render+send is attempted again, so bounded retry actually re-sends and a
 * permanently failing message is ultimately rejected to the DLQ
 * ({@code defaultRequeueRejected=false}) rather than silently ACKed.
 */
@Component
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    /** Sentinel: the event is already in a terminal-success state, nothing to do. */
    private static final long ALREADY_DONE = -1L;

    private final NotificationLogRepository logs;
    private final TemplateRenderer renderer;
    private final MailSenderAdapter mail;

    public UserRegisteredListener(NotificationLogRepository logs, TemplateRenderer renderer, MailSenderAdapter mail) {
        this.logs = logs;
        this.renderer = renderer;
        this.mail = mail;
    }

    @RabbitListener(queues = AmqpConfig.USER_REGISTERED_QUEUE)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event == null || !UserRegisteredEvent.TYPE.equals(event.type()) || event.eventId() == null) {
            log.warn("Пропуск события без типа {} или event_id", UserRegisteredEvent.TYPE);
            return;
        }

        UserRegisteredEvent.Payload payload = event.payload();
        if (payload == null || payload.email() == null) {
            log.warn("Событие {} без payload/email — пропуск", event.eventId());
            return;
        }

        long rowId = claimRow(event.eventId(), payload);
        if (rowId == ALREADY_DONE) {
            log.debug("Дубликат события {} в статусе sent — пропуск", event.eventId());
            return;
        }

        try {
            TemplateRenderer.RenderedEmail rendered =
                    renderer.render(TemplateRenderer.VERIFY_EMAIL, payload.email(), payload.verificationUrl());
            mail.send(payload.email(), rendered.subject(), rendered.body());
            logs.markSent(rowId, null, Instant.now());
            // Success marker for the chain: MDC correlation_id is set by
            // CorrelatedListenerAdvice from the AMQP header stamped by OutboxRelay.
            log.info("Уведомление отправлено: event_id={}, template={}",
                    event.eventId(), TemplateRenderer.VERIFY_EMAIL);
        } catch (RuntimeException ex) {
            // Record the attempt, then rethrow so the container's bounded retry /
            // dead-letter path (defaultRequeueRejected=false) takes over — never swallow.
            log.error("Не удалось отправить письмо для события {}: {}", event.eventId(), ex.getMessage());
            logs.markFailed(rowId, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Resolves the {@code notification_log} row to (re)use for this event, or
     * {@link #ALREADY_DONE} when it must be skipped.
     *
     * <ul>
     *   <li>No row → insert a fresh {@code queued} row.</li>
     *   <li>Existing {@code sent} row → skip (idempotent duplicate).</li>
     *   <li>Existing {@code failed}/{@code queued} row → reuse its id so render+send
     *       is retried and the status is transitioned by the caller.</li>
     *   <li>Lost the insert race ({@link DuplicateKeyException}) → re-read and apply
     *       the same status rule.</li>
     * </ul>
     */
    private long claimRow(String eventId, UserRegisteredEvent.Payload payload) {
        Optional<NotificationLog> existing = logs.findBySourceEventId(eventId);
        if (existing.isPresent()) {
            return reusableRowId(existing.get());
        }
        try {
            return logs.insertQueued(NotificationLog.queued(
                    parseUserId(payload.userId()),
                    payload.email(),
                    TemplateRenderer.VERIFY_EMAIL,
                    UserRegisteredEvent.TYPE,
                    eventId));
        } catch (DuplicateKeyException ex) {
            // A concurrent delivery won the UNIQUE(source_event_id) guard: re-read
            // its row and apply the same status-aware rule instead of dropping it.
            NotificationLog row = logs.findBySourceEventId(eventId)
                    .orElseThrow(() -> ex);
            return reusableRowId(row);
        }
    }

    /** {@code sent} is terminal success (skip); anything else is retried in-place. */
    private static long reusableRowId(NotificationLog row) {
        return row.status() == NotificationStatus.SENT ? ALREADY_DONE : row.id();
    }

    private static Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
