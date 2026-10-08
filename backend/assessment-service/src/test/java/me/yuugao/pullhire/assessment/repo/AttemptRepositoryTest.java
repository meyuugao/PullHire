package me.yuugao.pullhire.assessment.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.Attempt;
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
 * Pure unit checks (no DB) that {@link AttemptRepository} binds the served-item
 * columns, converts {@code served_at} to UTC {@link OffsetDateTime}, targets the
 * answer update by attempt id, and names the candidate/family parameters of the
 * prior-served-variant query.
 */
class AttemptRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private AttemptRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new AttemptRepository(jdbc);
    }

    @Test
    void insertBindsServedItemColumnsAndReturnsGeneratedId() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(9L);

        long id = repo.insert(attempt());

        assertThat(id).isEqualTo(9L);
        SqlParameterSource p = capturedSourceForObject(Long.class);
        assertThat(p.getValue("sessionId")).isEqualTo(5L);
        assertThat(p.getValue("itemId")).isEqualTo(11L);
        assertThat(p.getValue("itemOrder")).isEqualTo(1);
        assertThat(p.getValue("variantSeed")).isEqualTo(2026L);
        assertThat(p.getValue("answer")).isEqualTo("{\"choice\":\"b\"}");
        assertThat(p.getValue("isCorrect")).isEqualTo(true);
        assertThat(p.getValue("responseMs")).isEqualTo(4200);
        assertThat(p.getValue("proctoringFlags")).isEqualTo("[{\"type\":\"blur\"}]");
        assertThat(p.getValue("servedAt")).isEqualTo(OffsetDateTime.parse("2026-02-01T10:00:00Z"));
    }

    @Test
    void saveAnswerBindsGradedColumnsByAttemptId() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        assertThat(repo.saveAnswer(9L, "{\"choice\":\"c\"}", false, 1500, null)).isEqualTo(1);

        SqlParameterSource p = capturedSourceForUpdate();
        assertThat(p.getValue("attemptId")).isEqualTo(9L);
        assertThat(p.getValue("answer")).isEqualTo("{\"choice\":\"c\"}");
        assertThat(p.getValue("isCorrect")).isEqualTo(false);
        assertThat(p.getValue("responseMs")).isEqualTo(1500);
        assertThat(p.getValue("proctoringFlags")).isNull();
    }

    @Test
    void insertIgnoringConflictReturnsEmptyOnDuplicateWithoutThrowing() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(repo.insertIgnoringConflict(attempt())).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("ON CONFLICT").contains("DO NOTHING");
        assertThat(sql.getValue()).doesNotContain("ON CONFLICT (");
    }

    @Test
    void insertIgnoringConflictSwallowsDuplicateItemIdArmAndLeavesTxUsable() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        Attempt retry = new Attempt(0L, 5L, 11L, 2, 2026L, "{\"choice\":\"b\"}", true, 4200,
                "[{\"type\":\"blur\"}]", Instant.parse("2026-02-01T10:00:00Z"));

        assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> repo.insertIgnoringConflict(retry)))
                .isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("ON CONFLICT").contains("DO NOTHING");
        assertThat(sql.getValue()).doesNotContain("ON CONFLICT (");

        assertThat(repo.findBySession(5L)).isEmpty();
    }

    @Test
    void insertIgnoringConflictReturnsIdWhenWinningRace() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(9L));

        assertThat(repo.insertIgnoringConflict(attempt())).contains(9L);
    }

    @Test
    void findBySessionBindsSessionId() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repo.findBySession(5L)).isEmpty();

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("sessionId")).isEqualTo(5L);
    }

    @Test
    void priorServedVariantsBindsCandidateAndTemplateFamily() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(List.of(11L, 12L));

        assertThat(repo.priorServedVariants(42L, "fam-1")).containsExactly(11L, 12L);

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForList(anyString(), captor.capture(), eq(Long.class));
        assertThat(captor.getValue().getValue("candidateId")).isEqualTo(42L);
        assertThat(captor.getValue().getValue("templateFamily")).isEqualTo("fam-1");
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

    private static Attempt attempt() {
        return new Attempt(0L, 5L, 11L, 1, 2026L, "{\"choice\":\"b\"}", true, 4200,
                "[{\"type\":\"blur\"}]", Instant.parse("2026-02-01T10:00:00Z"));
    }
}
