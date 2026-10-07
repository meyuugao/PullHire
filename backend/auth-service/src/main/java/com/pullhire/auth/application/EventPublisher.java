package com.pullhire.auth.application;

import com.pullhire.auth.domain.Role;
import com.pullhire.events.EventEnvelope;
import com.pullhire.events.EventNames;
import com.pullhire.events.OutboxEventPublisher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes {@code user.registered} through the shared transactional outbox
 * ({@code docs/architecture.md §4.2/§4.3}). There is deliberately no
 * {@code RabbitTemplate} here: {@link OutboxEventPublisher} is the only publish
 * path, so the event commits with the registration transaction and the relay
 * delivers it afterwards.
 *
 * <p>OQ-13: the payload carries the fully rendered {@code verification_url},
 * never the raw {@code verification_token}.
 */
@Component
public class EventPublisher {

    public static final String TYPE_USER_REGISTERED = EventNames.USER_REGISTERED;
    private static final String PRODUCER = "auth-service";

    private final OutboxEventPublisher outbox;

    public EventPublisher(OutboxEventPublisher outbox) {
        this.outbox = outbox;
    }

    public void publishUserRegistered(long userId, String email, Role role, String verificationUrl,
                                      String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", Long.toString(userId));
        payload.put("email", email);
        payload.put("role", role.wire());
        payload.put("verification_url", verificationUrl);

        outbox.publish(EventEnvelope.of(TYPE_USER_REGISTERED, PRODUCER, correlationId, payload));
    }
}
