package com.pullhire.candidate.ingest;

import com.pullhire.candidate.application.CardProjectorService;
import com.pullhire.candidate.application.EventPublisher;
import com.pullhire.candidate.config.AmqpConfig;
import com.pullhire.candidate.domain.PrivacySettings;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.repo.PrivacySettingsRepository;
import com.pullhire.candidate.repo.ProcessedEventRepository;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.PublicCardRepository;
import com.pullhire.events.EventIdDedup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumer of {@code invitation.accepted} on {@code candidate.invitation-accepted}
 * ({@code docs/architecture.md §4.2}). Releases candidate contacts to the employer
 * after acceptance.
 *
 * <p><b>Idempotency (OQ-22).</b> The event id is claimed once in
 * {@code candidate.processed_events} (V6) inside the same transaction; a duplicate
 * is a no-op. The release itself is additionally guarded by the state-based
 * {@code candidate.public_cards.contact_released} flag so a redelivery after the
 * projection was reset cannot emit a second {@code candidate.contact_released}.
 * Publication happens only when the flag actually flips (0 rows → already released)
 * and at least one contact is present (F13).
 */
@Component
public class InvitationAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationAcceptedListener.class);

    private static final String DEDUP_TABLE = "candidate.processed_events";
    private static final String DEDUP_COLUMN = "event_id";

    private final EventIdDedup dedup;
    private final ProcessedEventRepository processedEvents;
    private final PrivacySettingsRepository privacy;
    private final CardProjectorService cards;
    private final PublicCardRepository publicCards;
    private final ProfileRepository profiles;
    private final EventPublisher events;

    public InvitationAcceptedListener(EventIdDedup dedup, ProcessedEventRepository processedEvents,
                                      PrivacySettingsRepository privacy, CardProjectorService cards,
                                      PublicCardRepository publicCards, ProfileRepository profiles,
                                      EventPublisher events) {
        this.dedup = dedup;
        this.processedEvents = processedEvents;
        this.privacy = privacy;
        this.cards = cards;
        this.publicCards = publicCards;
        this.profiles = profiles;
        this.events = events;
    }

    @RabbitListener(queues = AmqpConfig.INVITATION_ACCEPTED_QUEUE)
    @Transactional
    public void onInvitationAccepted(InvitationAcceptedEvent event) {
        if (event == null || !InvitationAcceptedEvent.TYPE.equals(event.type()) || event.payload() == null) {
            log.warn("Пропуск события без типа {} или payload", InvitationAcceptedEvent.TYPE);
            return;
        }

        String eventId = event.eventId();
        if (eventId != null && !eventId.isBlank()
                && (dedup.isDuplicate(eventId, DEDUP_TABLE, DEDUP_COLUMN) || !processedEvents.claim(eventId))) {
            log.debug("Дубликат invitation.accepted {} — пропуск", eventId);
            return;
        }

        Long profileId = parseId(event.payload().candidateId());
        if (profileId == null) {
            log.warn("Событие {} без корректного candidate_id — пропуск", eventId);
            return;
        }

        Optional<PrivacySettings> settings = privacy.findByProfileId(profileId);
        if (settings.isEmpty() || !settings.get().showContacts()) {
            log.debug("Контакты не раскрываются для candidate_id={} (show_contacts!=true)", profileId);
            return;
        }

        Profile profile = profiles.findById(profileId).orElse(null);
        if (profile == null) {
            log.warn("Профиль candidate_id={} не найден — событие раскрытия не опубликовано", profileId);
            return;
        }
        if (profile.contactEmail() == null && profile.contactPhone() == null) {
            log.info("Нет контактов для candidate_id={} — раскрытие пропущено (F13)", profileId);
            return;
        }

        // Ensure a card row exists so the release flag can be recorded even when the
        // profile was hidden (contact_released lives on public_cards).
        if (cards.current(profileId).isEmpty()) {
            cards.rebuild(profileId);
        }

        int changed = publicCards.markContactReleased(profileId);
        if (changed == 0) {
            log.debug("Контакты уже раскрыты для candidate_id={} — пропуск", profileId);
            return;
        }

        events.publishContactReleased(Long.toString(profileId), profile.contactEmail(),
                profile.contactPhone(), Instant.now());
        log.info("Контакты раскрыты: candidate_id={}", profileId);
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
