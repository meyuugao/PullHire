package me.yuugao.pullhire.fsp.adapter;

import me.yuugao.pullhire.fsp.domain.HistoryStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic mock registry behaviour and its reserved fixtures. */
class MockFspRegistryAdapterTest {

    private final MockFspRegistryAdapter adapter = new MockFspRegistryAdapter();

    @Test
    void errorMarkerProducesAdapterFailure() {
        RegistryHistory history = adapter.resolve("error");

        assertThat(history.failed()).isTrue();
        assertThat(history.status()).isEqualTo(HistoryStatus.UNKNOWN);
        assertThat(history.items()).isEmpty();
    }

    @Test
    void noHistoryMarkerProducesEmptySuccessfulHistory() {
        RegistryHistory history = adapter.resolve("no_history");

        assertThat(history.failed()).isFalse();
        assertThat(history.status()).isEqualTo(HistoryStatus.NO_HISTORY);
        assertThat(history.items()).isEmpty();
    }

    @Test
    void defaultIdProducesDeterministicPresentHistory() {
        RegistryHistory first = adapter.resolve("fsp-demo-001");
        RegistryHistory second = adapter.resolve("fsp-demo-001");

        assertThat(first.failed()).isFalse();
        assertThat(first.status()).isEqualTo(HistoryStatus.PRESENT);
        assertThat(first.items()).isNotEmpty();
        assertThat(first.items()).allSatisfy(item ->
                assertThat(item.externalAchievementId()).isNotBlank());
        assertThat(first.items()).isEqualTo(second.items());
    }
}
