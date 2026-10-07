package com.pullhire.events;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Broker-free test that the consumer advice exposes the chain id to ECS logs:
 * it must set MDC {@code correlation_id} from the consumed AMQP header during
 * handling and clear it afterwards.
 */
class CorrelatedListenerAdviceTest {

    private final CorrelatedListenerAdvice advice = new CorrelatedListenerAdvice();

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelatedListenerAdvice.MDC_KEY);
    }

    @Test
    void setsMdcFromAmqpHeaderDuringProceedAndClearsAfter() throws Throwable {
        MessageProperties props = new MessageProperties();
        props.setHeader(CorrelatedListenerAdvice.MDC_KEY, "req-abc");
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), props);

        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{message});
        when(invocation.proceed()).thenAnswer(inv -> {
            assertThat(MDC.get(CorrelatedListenerAdvice.MDC_KEY)).isEqualTo("req-abc");
            return "handled";
        });

        assertThat(advice.invoke(invocation)).isEqualTo("handled");
        assertThat(MDC.get(CorrelatedListenerAdvice.MDC_KEY)).isNull();
    }

    @Test
    void fallsBackToEnvelopeCorrelationId() throws Throwable {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", "42");
        EventEnvelope envelope = EventEnvelope.of(EventNames.USER_REGISTERED, "auth-service", "req-env", payload);

        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{envelope});
        when(invocation.proceed()).thenAnswer(inv -> {
            assertThat(MDC.get(CorrelatedListenerAdvice.MDC_KEY)).isEqualTo("req-env");
            return null;
        });

        advice.invoke(invocation);
        assertThat(MDC.get(CorrelatedListenerAdvice.MDC_KEY)).isNull();
    }

    @Test
    void leavesMdcUntouchedWhenNoCorrelation() throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{"no-correlation"});
        when(invocation.proceed()).thenAnswer(inv -> {
            assertThat(MDC.get(CorrelatedListenerAdvice.MDC_KEY)).isNull();
            return null;
        });

        advice.invoke(invocation);
        assertThat(MDC.get(CorrelatedListenerAdvice.MDC_KEY)).isNull();
    }
}
