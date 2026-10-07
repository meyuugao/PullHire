package com.pullhire.events;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-side reliability defaults ({@code docs/architecture.md §4.2/§4.3}):
 * bounded retries then a dead-letter exchange, plus publisher-confirm support.
 *
 * <ul>
 *   <li><b>Bounded retry + DLQ.</b> The default {@code rabbitListenerContainerFactory}
 *       is configured with a stateless retry interceptor (3 attempts) and
 *       {@code defaultRequeueRejected=false}; a permanently failing message is
 *       rejected and routed to the dead-letter exchange
 *       {@link #DEAD_LETTER_EXCHANGE} ({@code pullhire.events.dlx}).</li>
 *   <li><b>Queue convention.</b> Consumers declare their queues with
 *       {@link #durableQueue(String)}; each queue dead-letters to
 *       {@code <queue>.dlq} (e.g. {@code notification.user-registered.dlq}).</li>
 *   <li><b>Publisher confirms.</b> A {@link RabbitTemplateCustomizer} sets
 *       {@code mandatory=true} so unroutable messages are returned; enable
 *       confirms with {@code spring.rabbitmq.publisher-confirm-type: correlated}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SimpleRabbitListenerContainerFactory.class)
public class ConsumerRetryConfig {

    /** Dead-letter exchange shared by all consumer queues. */
    public static final String DEAD_LETTER_EXCHANGE = "pullhire.events.dlx";

    /** Suffix of the dead-letter queue for a source queue. */
    public static final String DEAD_LETTER_QUEUE_SUFFIX = ".dlq";

    /** Stateless retries before a message is dead-lettered. */
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 1_000L;
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;
    private static final long RETRY_BACKOFF_MAX_MS = 10_000L;

    /** The DLX; consumers bind their {@code <queue>.dlq} here. */
    @Bean
    @ConditionalOnMissingBean(name = "pullhireEventsDeadLetterExchange")
    public DirectExchange pullhireEventsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * A durable queue that dead-letters to {@code <queueName>.dlq} via
     * {@link #DEAD_LETTER_EXCHANGE} (routing key = source queue name).
     */
    public static QueueBuilder durableQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queueName);
    }

    /** Dead-letter queue name convention for a source queue. */
    public static String deadLetterQueueName(String queueName) {
        return queueName + DEAD_LETTER_QUEUE_SUFFIX;
    }

    /**
     * MDC correlation advice shared by the listener container factory: it sets
     * {@code correlation_id} in MDC from the consumed message's AMQP header so
     * consumer logs carry the producer's chain id. Registered as a bean because
     * the factory below injects it.
     */
    @Bean
    @ConditionalOnMissingBean
    public CorrelatedListenerAdvice correlatedListenerAdvice() {
        return new CorrelatedListenerAdvice();
    }

    /**
     * Overrides Boot's default factory so every {@code @RabbitListener} gets
     * bounded retry + reject-without-requeue + MDC correlation.
     */
    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            CorrelatedListenerAdvice correlatedListenerAdvice,
            @Qualifier("pullhireConsumerRetryInterceptor") MethodInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(correlatedListenerAdvice, retryInterceptor);
        return factory;
    }

    /** Stateless bounded retry; on exhaustion the message is rejected (→ DLQ). */
    @Bean(name = "pullhireConsumerRetryInterceptor")
    @ConditionalOnMissingBean(name = "pullhireConsumerRetryInterceptor")
    public MethodInterceptor pullhireConsumerRetryInterceptor(MessageRecoverer consumerMessageRecoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(MAX_RETRIES)
                .backOffOptions(RETRY_BACKOFF_MS, RETRY_BACKOFF_MULTIPLIER, RETRY_BACKOFF_MAX_MS)
                .recoverer(consumerMessageRecoverer)
                .build();
    }

    /** Recoverer that rejects without requeue so the DLX takes over. */
    @Bean
    @ConditionalOnMissingBean(name = "pullhireConsumerMessageRecoverer")
    public MessageRecoverer consumerMessageRecoverer() {
        return new RejectAndDontRequeueRecoverer(
                "Retries исчерпаны, сообщение направлено в " + DEAD_LETTER_EXCHANGE);
    }

    /** Publisher-confirm / mandatory support for the outbox relay's template. */
    @Bean
    @ConditionalOnMissingBean(name = "pullhireRabbitTemplateCustomizer")
    public RabbitTemplateCustomizer pullhireRabbitTemplateCustomizer() {
        return template -> template.setMandatory(true);
    }
}
