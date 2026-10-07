package com.pullhire.auth.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event broker topology ({@code docs/architecture.md §4.2}). auth-service only
 * publishes to {@code pullhire.events}; consumers (notification-service) own their
 * own queues and bindings.
 */
@Configuration
public class AmqpConfig {

    public static final String EVENTS_EXCHANGE = "pullhire.events";

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }
}
