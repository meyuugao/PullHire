package me.yuugao.pullhire.notification.config;

import me.yuugao.pullhire.events.ConsumerRetryConfig;
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
 * Event broker topology ({@code docs/architecture.md §4.2}). The consumer owns
 * its durable queue and binding; auth-service only publishes to
 * {@code pullhire.events}.
 *
 * <p>S2: retry/DLQ mechanics come from {@link ConsumerRetryConfig} (auto-configured):
 * it provides the dead-letter exchange {@code pullhire.events.dlx}, the
 * {@code defaultRequeueRejected=false} bounded-retry
 * {@code rabbitListenerContainerFactory}, the correlation MDC advice and
 * publisher-confirm support. This class therefore declares only the
 * notification-owned topology — the topic exchange, the durable
 * {@code notification.user-registered} queue (dead-lettering to
 * {@code notification.user-registered.dlq}), and its dead-letter queue/binding.
 * No exchange/bean defined by the auto-configuration is duplicated here.
 */
@Configuration
public class AmqpConfig {

    public static final String EVENTS_EXCHANGE = "pullhire.events";
    public static final String USER_REGISTERED_QUEUE = "notification.user-registered";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String CONTACT_RELEASED_QUEUE = "notification.candidate-contact-released";
    public static final String CONTACT_RELEASED_ROUTING_KEY = "candidate.contact_released";

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    /** Durable source queue; on exhaustion rejects to {@code pullhire.events.dlx}. */
    @Bean
    public Queue userRegisteredQueue() {
        return ConsumerRetryConfig.durableQueue(USER_REGISTERED_QUEUE).build();
    }

    /** Dead-letter queue for the source queue ({@code <queue>.dlq} convention). */
    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return QueueBuilder.durable(ConsumerRetryConfig.deadLetterQueueName(USER_REGISTERED_QUEUE)).build();
    }

    @Bean
    public Binding userRegisteredBinding(
            @Qualifier("userRegisteredQueue") Queue userRegisteredQueue,
            TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(pullhireEventsExchange)
                .with(USER_REGISTERED_ROUTING_KEY);
    }

    /** Binds {@code notification.user-registered.dlq} to the shared DLX. */
    @Bean
    public Binding userRegisteredDeadLetterBinding(
            @Qualifier("userRegisteredDeadLetterQueue") Queue userRegisteredDeadLetterQueue,
            @Qualifier("pullhireEventsDeadLetterExchange") DirectExchange pullhireEventsDeadLetterExchange) {
        return BindingBuilder.bind(userRegisteredDeadLetterQueue)
                .to(pullhireEventsDeadLetterExchange)
                .with(USER_REGISTERED_QUEUE);
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
                .with(CONTACT_RELEASED_ROUTING_KEY);
    }

    /** Binds {@code notification.candidate-contact-released.dlq} to the shared DLX. */
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
