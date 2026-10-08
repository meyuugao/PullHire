package me.yuugao.pullhire.auth.web;

import me.yuugao.pullhire.auth.application.AuthService;
import me.yuugao.pullhire.auth.domain.ConsentCommand;
import me.yuugao.pullhire.auth.domain.Role;
import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.web.dto.ConsentEntry;
import me.yuugao.pullhire.auth.web.dto.LoginRequest;
import me.yuugao.pullhire.auth.web.dto.MessageResponse;
import me.yuugao.pullhire.auth.web.dto.RefreshRequest;
import me.yuugao.pullhire.auth.web.dto.RegisterRequest;
import me.yuugao.pullhire.auth.web.dto.RegisterResponse;
import me.yuugao.pullhire.auth.web.dto.TokenResponse;
import me.yuugao.pullhire.auth.web.dto.VerifyEmailRequest;
import me.yuugao.pullhire.events.CorrelationMdcFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Auth endpoints ({@code docs/api.md §3.1}): register, verify-email, login, refresh. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        List<ConsentCommand> consents = request.consents() == null
                ? List.of()
                : request.consents().stream().map(ConsentEntry::toCommand).toList();
        User user = authService.register(
                request.email(),
                request.password(),
                Role.fromWire(request.role()),
                consents,
                requestId(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(user));
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return new MessageResponse("email_verified");
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    private static String requestId(HttpServletRequest request) {
        String header = request.getHeader(ApiVersionFilter.REQUEST_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        // D6: no inbound X-Request-Id → CorrelationMdcFilter generated one into MDC
        // for the same request; carry that id on the envelope so the event still
        // joins the trace chain (mirrors CorrelationMdcFilter.HEADER/MDC_KEY).
        String mdc = MDC.get(CorrelationMdcFilter.MDC_KEY);
        return (mdc == null || mdc.isBlank()) ? null : mdc;
    }
}
