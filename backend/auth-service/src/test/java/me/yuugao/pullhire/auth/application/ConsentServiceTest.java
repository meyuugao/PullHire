package me.yuugao.pullhire.auth.application;

import me.yuugao.pullhire.auth.domain.Consent;
import me.yuugao.pullhire.auth.domain.ConsentCommand;
import me.yuugao.pullhire.auth.domain.ConsentType;
import me.yuugao.pullhire.auth.repo.ConsentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Append-only consent logic with a mocked repository. */
class ConsentServiceTest {

    @Test
    void appendInsertsOneRowPerCommandAndNeverUpdates() {
        ConsentRepository repository = mock(ConsentRepository.class);
        ConsentService service = new ConsentService(repository);

        service.append(7L, List.of(
                new ConsentCommand(ConsentType.DATA_PROCESSING, true, "v1"),
                new ConsentCommand(ConsentType.PROFILE_PUBLICATION, false, "v1")));

        verify(repository, times(1)).insert(7L, ConsentType.DATA_PROCESSING, true, "v1");
        verify(repository, times(1)).insert(7L, ConsentType.PROFILE_PUBLICATION, false, "v1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void currentReturnsLatestPerTypeFromRepository() {
        ConsentRepository repository = mock(ConsentRepository.class);
        when(repository.findLatestByUser(7L)).thenReturn(List.of(
                new Consent(2L, 7L, ConsentType.DATA_PROCESSING, true, "v2", Instant.now(), null, Instant.now())));
        ConsentService service = new ConsentService(repository);

        List<Consent> current = service.current(7L);

        assertThat(current).hasSize(1);
        assertThat(current.get(0).policyVersion()).isEqualTo("v2");
        verify(repository).findLatestByUser(7L);
    }
}
