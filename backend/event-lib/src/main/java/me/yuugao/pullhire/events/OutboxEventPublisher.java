package me.yuugao.pullhire.events;

/**
 * The single publish API business services inject. It has no broker path: it
 * only builds an {@link EventEnvelope} and hands it to {@link OutboxWriter}, so
 * a service cannot accidentally bypass the transactional outbox with a direct
 * {@code RabbitTemplate.convertAndSend} (S2 binding decision).
 *
 * <p>Call it inside the same transaction as the state change; the row commits
 * with that transaction and {@link OutboxRelay} delivers it afterwards.
 */
public class OutboxEventPublisher {

    private final OutboxWriter writer;

    public OutboxEventPublisher(OutboxWriter writer) {
        this.writer = writer;
    }

    /** Publishes a pre-built envelope through the outbox. */
    public void publish(EventEnvelope envelope) {
        writer.write(envelope);
    }

    /** Builds a fresh envelope ({@code version=1}, new id, now) and stores it. */
    public void publish(String type, String producer, String correlationId,
                        java.util.Map<String, Object> payload) {
        writer.write(EventEnvelope.of(type, producer, correlationId, payload));
    }
}
