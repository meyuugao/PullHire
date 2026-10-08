package me.yuugao.pullhire.auth.web;

import me.yuugao.pullhire.auth.config.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the RS256 public key as a JWKS document ({@code docs/architecture.md §6.1},
 * {@code docs/api.md §3.1}) so every service and the gateway can verify tokens locally.
 */
@RestController
public class JwksController {

    private final RsaKeyProvider keys;

    public JwksController(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keys.jwks();
    }
}
