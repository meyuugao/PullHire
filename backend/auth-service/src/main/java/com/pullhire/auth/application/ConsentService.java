package com.pullhire.auth.application;

import com.pullhire.auth.domain.Consent;
import com.pullhire.auth.domain.ConsentCommand;
import com.pullhire.auth.repo.ConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 152-FZ consent journal ({@code docs/data_model.md §3.4}). Append-only: granting
 * or revoking a consent always inserts a new row; the current state is the latest
 * row per {@code (user_id, consent_type)}.
 */
@Service
public class ConsentService {

    private final ConsentRepository consents;

    public ConsentService(ConsentRepository consents) {
        this.consents = consents;
    }

    /** Appends one row per command; never updates existing rows. */
    @Transactional
    public void append(long userId, List<ConsentCommand> commands) {
        for (ConsentCommand command : commands) {
            consents.insert(userId, command.type(), command.granted(), command.policyVersion());
        }
    }

    /** Current consent state (latest per type) for a user. */
    public List<Consent> current(long userId) {
        return consents.findLatestByUser(userId);
    }
}
