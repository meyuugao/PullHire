package com.pullhire.events;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for event-lib: a service only has to add the
 * dependency and gets the transactional-outbox writer/publisher/relay, the
 * consumer dedup helper, the correlation MDC filter, consumer retry/DLQ defaults
 * and publisher-confirm support ({@link ConsumerRetryConfig}).
 *
 * <p>Registered through
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * Every bean is conditional, so a service may override any of them.
 */
@AutoConfiguration
@ConditionalOnClass({JsonMapper.class, JdbcTemplate.class})
@EnableConfigurationProperties(OutboxProperties.class)
@Import(ConsumerRetryConfig.class)
public class EventAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate) {
        return new OutboxWriter(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventPublisher outboxEventPublisher(OutboxWriter outboxWriter) {
        return new OutboxEventPublisher(outboxWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventIdDedup eventIdDedup(JdbcTemplate jdbcTemplate) {
        return new EventIdDedup(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationMdcFilter correlationMdcFilter() {
        return new CorrelationMdcFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pullhire.outbox", name = "enabled",
            havingValue = "true")
    public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate,
                                   org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
                                   OutboxProperties outboxProperties) {
        return new OutboxRelay(jdbcTemplate, rabbitTemplate, outboxProperties);
    }

    /** Enables scheduling only when the relay is explicitly opted in. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "pullhire.outbox", name = "enabled",
            havingValue = "true")
    static class OutboxSchedulingConfiguration {
    }
}
