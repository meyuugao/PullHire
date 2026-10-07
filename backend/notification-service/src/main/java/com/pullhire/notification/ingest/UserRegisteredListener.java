package com.pullhire.notification.ingest;

import com.pullhire.notification.config.AmqpConfig;
import com.pullhire.notification.domain.NotificationLog;
import com.pullhire.notification.mail.MailSenderAdapter;
import com.pullhire.notification.mail.TemplateRenderer;
import com.pullhire.notification.repo.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumer of {@code user.registered} on {@code notification.user-registered}
 * ({@code docs/architecture.md §4.2}). Dedups by {@code event_id} against the
 * UNIQUE {@code notification_log.source_event_id}, renders the {@code verify_email}
 * template, sends via SMTP, and records the {@code queued → sent/failed} transition.
 */
@Component
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

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
        if (logs.existsBySourceEventId(event.eventId())) {
            log.debug("Дубликат события {} — пропуск", event.eventId());
            return;
        }

        UserRegisteredEvent.Payload payload = event.payload();
        if (payload == null || payload.email() == null) {
            log.warn("Событие {} без payload/email — пропуск", event.eventId());
            return;
        }

        long rowId;
        try {
            rowId = logs.insertQueued(NotificationLog.queued(
                    parseUserId(payload.userId()),
                    payload.email(),
                    TemplateRenderer.VERIFY_EMAIL,
                    UserRegisteredEvent.TYPE,
                    event.eventId()));
        } catch (DuplicateKeyException ex) {
            log.debug("Событие {} уже обработано параллельно — пропуск", event.eventId());
            return;
        }

        try {
            TemplateRenderer.RenderedEmail rendered =
                    renderer.render(TemplateRenderer.VERIFY_EMAIL, payload.email(), payload.verificationToken());
            mail.send(payload.email(), rendered.subject(), rendered.body());
            logs.markSent(rowId, null, Instant.now());
        } catch (RuntimeException ex) {
            log.error("Не удалось отправить письмо для события {}: {}", event.eventId(), ex.getMessage());
            logs.markFailed(rowId, ex.getMessage());
        }
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
