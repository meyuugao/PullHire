package me.yuugao.pullhire.candidate.config;

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
 * Event broker topology ({@code docs/architecture.md §4.2}). candidate-service
 * both publishes (card/contact events via the outbox) and consumes
 * {@code invitation.accepted} to release contacts. It therefore declares the
 * shared topic exchange and its own durable consumer queue + binding.
 *
 * <p>S2: retry/DLQ mechanics come from {@link ConsumerRetryConfig} (auto-configured):
 * the dead-letter exchange {@code pullhire.events.dlx}, the bounded-retry
 * {@code rabbitListenerContainerFactory}, the correlation MDC advice and
 * publisher-confirm support. This class declares only the candidate-owned
 * topology — the durable {@code candidate.invitation-accepted} queue
 * (dead-lettering to {@code candidate.invitation-accepted.dlq}) and its bindings.
 */
@Configuration
public class AmqpConfig {

    public static final String INVITATION_ACCEPTED_QUEUE = "candidate.invitation-accepted";

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }

    /** Durable source queue; on exhaustion rejects to {@code pullhire.events.dlx}. */
    @Bean
    public Queue invitationAcceptedQueue() {
        return ConsumerRetryConfig.durableQueue(INVITATION_ACCEPTED_QUEUE).build();
    }

    /** Dead-letter queue for the source queue ({@code <queue>.dlq} convention). */
    @Bean
    public Queue invitationAcceptedDeadLetterQueue() {
        return QueueBuilder.durable(ConsumerRetryConfig.deadLetterQueueName(INVITATION_ACCEPTED_QUEUE)).build();
    }

    @Bean
    public Binding invitationAcceptedBinding(
            @Qualifier("invitationAcceptedQueue") Queue invitationAcceptedQueue,
            TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(invitationAcceptedQueue)
                .to(pullhireEventsExchange)
                .with(EventNames.INVITATION_ACCEPTED);
    }

    /** Binds {@code candidate.invitation-accepted.dlq} to the shared DLX. */
    @Bean
    public Binding invitationAcceptedDeadLetterBinding(
            @Qualifier("invitationAcceptedDeadLetterQueue") Queue invitationAcceptedDeadLetterQueue,
            @Qualifier("pullhireEventsDeadLetterExchange") DirectExchange pullhireEventsDeadLetterExchange) {
        return BindingBuilder.bind(invitationAcceptedDeadLetterQueue)
                .to(pullhireEventsDeadLetterExchange)
                .with(INVITATION_ACCEPTED_QUEUE);
    }

    /** Envelope/payload JSON (Jackson 3, {@code tools.jackson}) for consumed events. */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
