package me.yuugao.pullhire.fsp.config;

import me.yuugao.pullhire.events.EventNames;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event broker topology ({@code docs/architecture.md §4.2}). fsp-service is
 * publish-only: it declares the shared topic exchange so the outbox relay can
 * publish {@code fsp.linked} / {@code fsp.achievements_updated}, but owns no
 * consumer queue. Retry/DLQ defaults from event-lib are irrelevant here.
 */
@Configuration
public class AmqpConfig {

    @Bean
    public TopicExchange pullhireEventsExchange() {
        return new TopicExchange(EventNames.EXCHANGE, true, false);
    }
}
