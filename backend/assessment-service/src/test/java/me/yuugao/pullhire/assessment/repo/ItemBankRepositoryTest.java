package me.yuugao.pullhire.assessment.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.Item;
import me.yuugao.pullhire.assessment.domain.ItemKind;
import me.yuugao.pullhire.assessment.domain.ItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no DB) that {@link ItemBankRepository} strips domain enums to
 * their wire values, binds the jsonb payload, converts timestamps to UTC
 * {@link OffsetDateTime} and names every SQL parameter correctly.
 */
class ItemBankRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private ItemBankRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new ItemBankRepository(jdbc);
    }

    @Test
    void insertBindsAnchorColumnsPayloadAndUtcTimestamps() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(7L);

        long id = repo.insert(anchor());

        assertThat(id).isEqualTo(7L);
        SqlParameterSource p = capturedSourceForObject(Long.class);
        assertThat(p.getValue("itemKind")).isEqualTo("anchor");
        assertThat(p.getValue("templateFamily")).isNull();
        assertThat(p.getValue("conceptCode")).isEqualTo("concept-a");
        assertThat(p.getValue("specializationCode")).isEqualTo("spec-1");
        assertThat(p.getValue("difficulty")).isEqualTo(new BigDecimal("1.250"));
        assertThat(p.getValue("isProvisional")).isEqualTo(true);
        assertThat(p.getValue("exposureLimit")).isEqualTo(100);
        assertThat(p.getValue("exposureCount")).isEqualTo(0);
        assertThat(p.getValue("payload")).isEqualTo("{\"q\":1}");
        assertThat(p.getValue("status")).isEqualTo("active");
        assertThat(p.getValue("createdAt")).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(p.getValue("updatedAt")).isEqualTo(OffsetDateTime.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void insertBindsTemplateFamily() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);

        repo.insert(template());

        SqlParameterSource p = capturedSourceForObject(Long.class);
        assertThat(p.getValue("itemKind")).isEqualTo("template");
        assertThat(p.getValue("templateFamily")).isEqualTo("fam-1");
        assertThat(p.getValue("status")).isEqualTo("active");
    }

    @Test
    void findActiveBySpecializationBindsCode() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        List<Item> found = repo.findActiveBySpecialization("spec-9");

        assertThat(found).isEmpty();
        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("specializationCode")).isEqualTo("spec-9");
    }

    @Test
    void incrementExposureBindsItemId() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        assertThat(repo.incrementExposure(9L)).isEqualTo(1);
        assertThat(capturedSourceForUpdate().getValue("itemId")).isEqualTo(9L);
    }

    @Test
    void isExposureExhaustedReadsBooleanPredicate() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Boolean.class))).thenReturn(true);

        assertThat(repo.isExposureExhausted(3L)).isTrue();
        assertThat(capturedSourceForObject(Boolean.class).getValue("itemId")).isEqualTo(3L);
    }

    @Test
    void retireBindsItemId() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        assertThat(repo.retire(4L)).isEqualTo(1);
        assertThat(capturedSourceForUpdate().getValue("itemId")).isEqualTo(4L);
    }

    private SqlParameterSource capturedSourceForObject(Class<?> requiredType) {
        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), captor.capture(), eq(requiredType));
        return captor.getValue();
    }

    private SqlParameterSource capturedSourceForUpdate() {
        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        return captor.getValue();
    }

    private static Item anchor() {
        return new Item(0L, ItemKind.ANCHOR, null, "concept-a", "spec-1", new BigDecimal("1.250"), true,
                100, 0, "{\"q\":1}", ItemStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
    }

    private static Item template() {
        return new Item(0L, ItemKind.TEMPLATE, "fam-1", "concept-b", "spec-1", new BigDecimal("0.500"), false,
                null, 0, "{\"q\":2}", ItemStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
    }
}
