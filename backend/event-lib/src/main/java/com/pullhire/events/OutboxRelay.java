package com.pullhire.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * At-least-once outbox relay ({@code docs/architecture.md §4.3}).
 *
 * <p>Every {@code pullhire.outbox.interval} it selects unpublished
 * {@code outbox_events} rows and republishes each to
 * {@code pullhire.events} with routing key {@code event.type}. A row's
 * {@code published_at} is set only after the broker confirms the publish, so a
 * broker outage (or a NACK) simply leaves the row for the next poll — no event
 * is lost.
 *
 * <p>The routing key equals the event name so consumers can bind
 * {@code <event_name>} or wildcard patterns such as {@code assessment.*}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String SELECT_UNPUBLISHED = "SELECT id, event_id, event_type, "
            + "payload::text AS payload, correlation_id FROM outbox_events "
            + "WHERE published_at IS NULL ORDER BY id LIMIT ?";

    private static final String MARK_PUBLISHED =
            "UPDATE outbox_events SET published_at = now() WHERE id = ? AND published_at IS NULL";

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;

    public OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${pullhire.outbox.interval:5s}")
    public void relay() {
        for (OutboxMessage message : findUnpublished()) {
            try {
                publish(message);
            } catch (Exception ex) {
                // Leave the row unpublished; the next poll retries it.
                log.warn("Outbox relay: сообщение {} ({}) не доставлено: {}",
                        message.id(), message.eventType(), ex.getMessage());
            }
        }
    }

    private List<OutboxMessage> findUnpublished() {
        return jdbc.query(SELECT_UNPUBLISHED,
                (rs, rowNum) -> new OutboxMessage(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("correlation_id")),
                properties.getBatchSize());
    }

    private void publish(OutboxMessage message) throws Exception {
        CorrelationData correlation = new CorrelationData(message.eventId());
        rabbitTemplate.convertAndSend(EventNames.EXCHANGE, message.eventType(), message.payload(),
                amqpMessage -> {
                    MessageProperties props = amqpMessage.getMessageProperties();
                    props.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    props.setContentEncoding(StandardCharsets.UTF_8.name());
                    if (message.correlationId() != null) {
                        props.setHeader(CorrelatedListenerAdvice.MDC_KEY, message.correlationId());
                    }
                    return amqpMessage;
                },
                correlation);

        CorrelationData.Confirm confirm = correlation.getFuture()
                .get(properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.ack()) {
            log.warn("Outbox relay: брокер отклонил сообщение {} (nack: {})",
                    message.id(), confirm.reason());
            return;
        }
        jdbc.update(MARK_PUBLISHED, message.id());
    }

    /** Flat row projection of an unpublished outbox entry. */
    private record OutboxMessage(long id, String eventId, String eventType, String payload,
                                 String correlationId) {
    }
}
