package me.yuugao.pullhire.events;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

/**
 * AOP advice added to the listener container advice chain so that consumer
 * handling runs with MDC key {@code correlation_id} set from the consumed
 * {@link EventEnvelope#correlationId()} (or the {@code correlation_id} AMQP
 * header that {@link OutboxRelay} stamps on every published message). This
 * makes the chain id visible in the consumer's ECS logs
 * ({@code docs/architecture.md §7.1}).
 *
 * <p>The id is restored/removed in {@code finally}, so it never leaks onto a
 * reused listener thread.
 */
public class CorrelatedListenerAdvice implements MethodInterceptor {

    public static final String MDC_KEY = CorrelationMdcFilter.MDC_KEY;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String correlationId = extract(invocation.getArguments());
        if (correlationId == null) {
            return invocation.proceed();
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, correlationId);
        try {
            return invocation.proceed();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }

    private static String extract(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof EventEnvelope envelope && envelope.correlationId() != null) {
                return envelope.correlationId();
            }
            if (argument instanceof Message message) {
                Object header = message.getMessageProperties().getHeader(MDC_KEY);
                if (header != null) {
                    return header.toString();
                }
            }
        }
        return null;
    }
}
