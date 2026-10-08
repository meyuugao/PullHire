package me.yuugao.pullhire.candidate.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S0 liveness/readiness placeholders ({@code docs/architecture.md §5.3}).
 * Real DB/broker readiness checks arrive in S1+; S0 stubs stay connection-free.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ResponseEntity<Void> healthz() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/readyz")
    public ResponseEntity<Void> readyz() {
        return ResponseEntity.ok().build();
    }
}
