package me.yuugao.pullhire.assessment.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.Cooldown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no DB) that {@link CooldownRepository} binds the upsert
 * columns with the default {@code grade_change} scope, converts
 * {@code available_after} to a UTC {@link OffsetDateTime}, and scopes the read to
 * {@code (candidate_id, scope)} ({@code docs/data_model.md §6.1}).
 */
class CooldownRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private CooldownRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new CooldownRepository(jdbc);
    }

    @Test
    void upsertBindsColumnsWithDefaultScopeAndConvertsAvailableAfterToUtc() {
        Cooldown row = new Cooldown(1L, 42L, CooldownRepository.SCOPE_GRADE_CHANGE,
                Instant.parse("2026-02-01T10:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), anyMapper()))
                .thenReturn(row);

        Cooldown result = repo.upsert(42L, Instant.parse("2026-02-01T10:00:00Z"));

        assertThat(result).isSameAs(row);
        SqlParameterSource p = capturedForObject();
        assertThat(p.getValue("candidateId")).isEqualTo(42L);
        assertThat(p.getValue("scope")).isEqualTo(CooldownRepository.SCOPE_GRADE_CHANGE);
        assertThat(p.getValue("availableAfter")).isEqualTo(OffsetDateTime.parse("2026-02-01T10:00:00Z"));
    }

    @Test
    void findActiveBindsCandidateAndScopeAndReturnsRow() {
        Cooldown row = new Cooldown(1L, 42L, CooldownRepository.SCOPE_GRADE_CHANGE,
                Instant.parse("2026-02-01T10:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(jdbc.query(anyString(), any(SqlParameterSource.class), anyMapper()))
                .thenReturn(List.of(row));

        assertThat(repo.findActive(42L, CooldownRepository.SCOPE_GRADE_CHANGE)).contains(row);

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), captor.capture(), anyMapper());
        assertThat(captor.getValue().getValue("candidateId")).isEqualTo(42L);
        assertThat(captor.getValue().getValue("scope")).isEqualTo(CooldownRepository.SCOPE_GRADE_CHANGE);
    }

    @Test
    void findActiveReturnsEmptyWhenNoRow() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), anyMapper()))
                .thenReturn(List.of());

        assertThat(repo.findActive(99L)).isEmpty();
    }

    private SqlParameterSource capturedForObject() {
        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), captor.capture(), anyMapper());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<Cooldown> anyMapper() {
        return any(RowMapper.class);
    }
}
