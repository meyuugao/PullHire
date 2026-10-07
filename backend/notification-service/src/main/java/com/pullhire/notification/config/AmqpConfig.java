package com.pullhire.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event broker topology ({@code docs/architecture.md §4.2}). The consumer owns
 * its durable queue and binding; auth-service only publishes to
 * {@code pullhire.events}. S1 handles {@code user.registered} only.
 */
@Configuration
public class AmqpConfig {

    public static final String EVENTS_EXCHANGE = "pullhire.events";
    public static final String USER_REGISTERED_QUEUE = "notification.user-registered";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE).build();
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange pullhireEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
                .to(pullhireEventsExchange)
                .with(USER_REGISTERED_ROUTING_KEY);
    }

    /** Envelope/payload JSON (Jackson 3, {@code tools.jackson}) for consumed events. */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
