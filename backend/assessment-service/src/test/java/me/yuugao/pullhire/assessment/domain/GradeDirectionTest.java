package me.yuugao.pullhire.assessment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure checks of the {@link GradeDirection} wire mapping
 * ({@code docs/data_model.md §6.1}): the DB CHECK values round-trip through
 * {@code wire()}/{@code fromWire()}.
 */
class GradeDirectionTest {

    @Test
    void wireMatchesDatabaseCheckValues() {
        assertThat(GradeDirection.INITIAL.wire()).isEqualTo("initial");
        assertThat(GradeDirection.UP.wire()).isEqualTo("up");
        assertThat(GradeDirection.DOWN.wire()).isEqualTo("down");
        assertThat(GradeDirection.RECHECK.wire()).isEqualTo("recheck");
    }

    @Test
    void fromWireParsesEveryValueAndIsCaseInsensitive() {
        assertThat(GradeDirection.fromWire("initial")).isEqualTo(GradeDirection.INITIAL);
        assertThat(GradeDirection.fromWire(" up ")).isEqualTo(GradeDirection.UP);
        assertThat(GradeDirection.fromWire("DOWN")).isEqualTo(GradeDirection.DOWN);
        assertThat(GradeDirection.fromWire("recheck")).isEqualTo(GradeDirection.RECHECK);
    }

    @Test
    void fromWireRejectsNullAndUnknown() {
        assertThatThrownBy(() -> GradeDirection.fromWire(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direction is required");
        assertThatThrownBy(() -> GradeDirection.fromWire("sideways"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown direction: sideways");
    }
}
