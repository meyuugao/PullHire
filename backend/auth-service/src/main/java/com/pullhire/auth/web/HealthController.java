package com.pullhire.auth.web;

import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness and readiness ({@code docs/architecture.md §5.3}).
 *
 * <ul>
 *   <li>{@code /healthz} — unconditional liveness; the process is up.</li>
 *   <li>{@code /readyz} — real readiness (S2): {@code SELECT 1} against the
 *       service database <em>and</em> a broker connection check; {@code 503}
 *       when either is down. The docker-compose TCP healthcheck is unchanged.</li>
 * </ul>
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final ConnectionFactory rabbitConnectionFactory;

    public HealthController(JdbcTemplate jdbc, ConnectionFactory rabbitConnectionFactory) {
        this.jdbc = jdbc;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Void> healthz() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/readyz")
    public ResponseEntity<Void> readyz() {
        if (databaseUp() && brokerUp()) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private boolean databaseUp() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean brokerUp() {
        try (Connection connection = rabbitConnectionFactory.createConnection()) {
            return connection.isOpen();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
