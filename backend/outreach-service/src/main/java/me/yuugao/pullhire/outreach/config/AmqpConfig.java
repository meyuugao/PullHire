package me.yuugao.pullhire.outreach.config;

import me.yuugao.pullhire.events.ConsumerRetryConfig;
import me.yuugao.pullhire.events.EventNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event broker topology ({@code docs/architecture.md §4.2}). outreach-service
 * consumes {@code candidate.contact_released} into its own durable queue and
 * publishes invitation events via the outbox relay (no publish topology here).
 *
 * <p>Retry/DLQ mechanics come from {@link ConsumerRetryConfig}
 * (auto-configured): the dead-letter exchange {@code pullhire.events.dlx}, the
 * bounded-retry {@code rabbitListenerContainerFactory} and publisher-confirm
 * support. This class declares only the outreach-owned topology — the durable
 * {@code outreach.candidate-contact-released} queue (dead-lettering to
 * {@code <queue>.dlq}) and its binding.
 */
@Configuration
public class AmqpConfig {

    public static final String CONTACT_RELEASED_QUEUE = "outreach.candidate-contact-released";

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }

    /** Durable source queue; on exhaustion rejects to {@code pullhire.events.dlx}. */
    @Bean
    public Queue contactReleasedQueue() {
        return ConsumerRetryConfig.durableQueue(CONTACT_RELEASED_QUEUE).build();
    }

    /** Dead-letter queue for the source queue ({@code <queue>.dlq} convention). */
    @Bean
    public Queue contactReleasedDeadLetterQueue() {
        return QueueBuilder.durable(ConsumerRetryConfig.deadLetterQueueName(CONTACT_RELEASED_QUEUE)).build();
    }

    @Bean
    public Binding contactReleasedBinding(
            @Qualifier("contactReleasedQueue") Queue contactReleasedQueue,
            TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(contactReleasedQueue)
                .to(pullhireEventsExchange)
                .with(EventNames.CANDIDATE_CONTACT_RELEASED);
    }

    /** Binds {@code outreach.candidate-contact-released.dlq} to the shared DLX. */
    @Bean
    public Binding contactReleasedDeadLetterBinding(
            @Qualifier("contactReleasedDeadLetterQueue") Queue contactReleasedDeadLetterQueue,
            @Qualifier("pullhireEventsDeadLetterExchange") DirectExchange pullhireEventsDeadLetterExchange) {
        return BindingBuilder.bind(contactReleasedDeadLetterQueue)
                .to(pullhireEventsDeadLetterExchange)
                .with(CONTACT_RELEASED_QUEUE);
    }

    /** Envelope/payload JSON (Jackson 3, {@code tools.jackson}) for consumed events. */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
