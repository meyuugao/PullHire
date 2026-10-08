package me.yuugao.pullhire.selection.config;

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
 * Event broker topology ({@code docs/architecture.md §4.2}). selection-service
 * is consumer-only: it projects {@code assessment.category_assigned},
 * {@code candidate.card_updated} and {@code fsp.achievements_updated} into its
 * own read model. It therefore declares the shared topic exchange and its own
 * durable consumer queues + bindings.
 *
 * <p>S2: retry/DLQ mechanics come from {@link ConsumerRetryConfig} (auto-configured):
 * the dead-letter exchange {@code pullhire.events.dlx}, the bounded-retry
 * {@code rabbitListenerContainerFactory}, the correlation MDC advice and
 * publisher-confirm support. This class declares only the selection-owned
 * topology — the durable {@code selection.*} queues (dead-lettering to
 * {@code <queue>.dlq}) and their bindings.
 */
@Configuration
public class AmqpConfig {

    public static final String CATEGORY_ASSIGNED_QUEUE = "selection.category-assigned";
    public static final String CARD_UPDATED_QUEUE = "selection.card-updated";
    public static final String ACHIEVEMENTS_UPDATED_QUEUE = "selection.achievements-updated";

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }

    /** Durable source queue; on exhaustion rejects to {@code pullhire.events.dlx}. */
    @Bean
    public Queue categoryAssignedQueue() {
        return ConsumerRetryConfig.durableQueue(CATEGORY_ASSIGNED_QUEUE).build();
    }

    /** Dead-letter queue for the source queue ({@code <queue>.dlq} convention). */
    @Bean
    public Queue categoryAssignedDeadLetterQueue() {
        return QueueBuilder.durable(ConsumerRetryConfig.deadLetterQueueName(CATEGORY_ASSIGNED_QUEUE)).build();
    }

    @Bean
    public Binding categoryAssignedBinding(
            @Qualifier("categoryAssignedQueue") Queue categoryAssignedQueue,
            TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(categoryAssignedQueue)
                .to(pullhireEventsExchange)
                .with(EventNames.ASSESSMENT_CATEGORY_ASSIGNED);
    }

    /** Binds {@code selection.category-assigned.dlq} to the shared DLX. */
    @Bean
    public Binding categoryAssignedDeadLetterBinding(
            @Qualifier("categoryAssignedDeadLetterQueue") Queue categoryAssignedDeadLetterQueue,
            @Qualifier("pullhireEventsDeadLetterExchange") DirectExchange pullhireEventsDeadLetterExchange) {
        return BindingBuilder.bind(categoryAssignedDeadLetterQueue)
                .to(pullhireEventsDeadLetterExchange)
                .with(CATEGORY_ASSIGNED_QUEUE);
    }

    /** Durable source queue; on exhaustion rejects to {@code pullhire.events.dlx}. */
    @Bean
    public Queue cardUpdatedQueue() {
        return ConsumerRetryConfig.durableQueue(CARD_UPDATED_QUEUE).build();
    }

    /** Dead-letter queue for the source queue ({@code <queue>.dlq} convention). */
    @Bean
    public Queue cardUpdatedDeadLetterQueue() {
        return QueueBuilder.durable(ConsumerRetryConfig.deadLetterQueueName(CARD_UPDATED_QUEUE)).build();
    }

    @Bean
    public Binding cardUpdatedBinding(
            @Qualifier("cardUpdatedQueue") Queue cardUpdatedQueue,
            TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(cardUpdatedQueue)
                .to(pullhireEventsExchange)
                .with(EventNames.CANDIDATE_CARD_UPDATED);
    }

    /** Binds {@code selection.card-updated.dlq} to the shared DLX. */
    @Bean
    public Binding cardUpdatedDeadLetterBinding(
            @Qualifier("cardUpdatedDeadLetterQueue") Queue cardUpdatedDeadLetterQueue,
            @Qualifier("pullhireEventsDeadLetterExchange") DirectExchange pullhireEventsDeadLetterExchange) {
        return BindingBuilder.bind(cardUpdatedDeadLetterQueue)
                .to(pullhireEventsDeadLetterExchange)
                .with(CARD_UPDATED_QUEUE);
    }

    /** Durable source queue; on exhaustion rejects to {@code pullhire.events.dlx}. */
    @Bean
    public Queue achievementsUpdatedQueue() {
        return ConsumerRetryConfig.durableQueue(ACHIEVEMENTS_UPDATED_QUEUE).build();
    }

    /** Dead-letter queue for the source queue ({@code <queue>.dlq} convention). */
    @Bean
    public Queue achievementsUpdatedDeadLetterQueue() {
        return QueueBuilder.durable(ConsumerRetryConfig.deadLetterQueueName(ACHIEVEMENTS_UPDATED_QUEUE)).build();
    }

    @Bean
    public Binding achievementsUpdatedBinding(
            @Qualifier("achievementsUpdatedQueue") Queue achievementsUpdatedQueue,
            TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(achievementsUpdatedQueue)
                .to(pullhireEventsExchange)
                .with(EventNames.FSP_ACHIEVEMENTS_UPDATED);
    }

    /** Binds {@code selection.achievements-updated.dlq} to the shared DLX. */
    @Bean
    public Binding achievementsUpdatedDeadLetterBinding(
            @Qualifier("achievementsUpdatedDeadLetterQueue") Queue achievementsUpdatedDeadLetterQueue,
            @Qualifier("pullhireEventsDeadLetterExchange") DirectExchange pullhireEventsDeadLetterExchange) {
        return BindingBuilder.bind(achievementsUpdatedDeadLetterQueue)
                .to(pullhireEventsDeadLetterExchange)
                .with(ACHIEVEMENTS_UPDATED_QUEUE);
    }

    /** Envelope/payload JSON (Jackson 3, {@code tools.jackson}) for consumed events. */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
