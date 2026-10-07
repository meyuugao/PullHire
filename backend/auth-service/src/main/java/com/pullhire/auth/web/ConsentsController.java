package com.pullhire.auth.web;

import com.pullhire.auth.application.ConsentService;
import com.pullhire.auth.domain.Consent;
import com.pullhire.auth.domain.ConsentCommand;
import com.pullhire.auth.domain.ConsentType;
import com.pullhire.auth.web.dto.ConsentEntry;
import com.pullhire.auth.web.dto.ConsentItem;
import com.pullhire.auth.web.dto.ConsentUpdateRequest;
import com.pullhire.auth.web.dto.ConsentsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 152-FZ consents ({@code docs/api.md §3.1}). PUT appends new rows; the response is
 * the current state over all four consent types ({@code docs/data_model.md §3.4}).
 */
@RestController
@RequestMapping("/auth/consents")
public class ConsentsController {

    private final ConsentService consentService;

    public ConsentsController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    public ConsentsResponse get(HttpServletRequest request) {
        long userId = MeController.requireUserId(request);
        return response(userId, consentService.current(userId));
    }

    @PutMapping
    public ConsentsResponse update(@Valid @RequestBody ConsentUpdateRequest body, HttpServletRequest request) {
        long userId = MeController.requireUserId(request);
        List<ConsentCommand> commands = body.consents().stream().map(ConsentEntry::toCommand).toList();
        consentService.append(userId, commands);
        return response(userId, consentService.current(userId));
    }

    private static ConsentsResponse response(long userId, List<Consent> current) {
        Map<ConsentType, Consent> latest = current.stream()
                .collect(Collectors.toMap(Consent::consentType, Function.identity(), (first, second) -> first));
        List<ConsentItem> items = Arrays.stream(ConsentType.values())
                .map(type -> {
                    Consent consent = latest.get(type);
                    return consent == null
                            ? new ConsentItem(type.wire(), false, null, null, null)
                            : new ConsentItem(type.wire(), consent.granted(), consent.policyVersion(),
                                    consent.grantedAt(), consent.revokedAt());
                })
                .toList();
        return new ConsentsResponse(Long.toString(userId), items);
    }
}
