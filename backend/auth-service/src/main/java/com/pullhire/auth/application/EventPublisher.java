package com.pullhire.auth.application;

import com.pullhire.auth.config.AmqpConfig;
import com.pullhire.auth.domain.Role;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes the {@code user.registered} event to {@code pullhire.events}
 * ({@code docs/architecture.md §4.2}). S1 keeps this a minimal, direct publish —
 * no outbox and no shared event library yet (that is S2).
 *
 * <pre>
 * {"event_id","type","version","occurred_at","producer","correlation_id","payload"}
 * </pre>
 */
@Component
public class EventPublisher {

    public static final String TYPE_USER_REGISTERED = "user.registered";
    private static final String PRODUCER = "auth-service";
    private static final int VERSION = 1;

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    public EventPublisher(RabbitTemplate rabbitTemplate, JsonMapper jsonMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
    }

    public void publishUserRegistered(long userId, String email, Role role, String verificationToken,
                                      String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", Long.toString(userId));
        payload.put("email", email);
        payload.put("role", role.wire());
        payload.put("verification_token", verificationToken);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", UUID.randomUUID().toString());
        event.put("type", TYPE_USER_REGISTERED);
        event.put("version", VERSION);
        event.put("occurred_at", Instant.now().toString());
        event.put("producer", PRODUCER);
        event.put("correlation_id", correlationId);
        event.put("payload", payload);

        String body = jsonMapper.writeValueAsString(event);
        MessagePostProcessor postProcessor = message -> {
            message.getMessageProperties().setContentType(MediaType.APPLICATION_JSON_VALUE);
            message.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
            return message;
        };
        rabbitTemplate.convertAndSend(AmqpConfig.EVENTS_EXCHANGE, TYPE_USER_REGISTERED, body, postProcessor);
    }
}
