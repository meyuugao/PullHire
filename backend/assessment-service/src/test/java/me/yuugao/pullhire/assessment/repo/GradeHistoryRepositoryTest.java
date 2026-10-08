package me.yuugao.pullhire.assessment.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.GradeDirection;
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
 * Pure unit checks (no DB) that {@link GradeHistoryRepository} binds the append
 * columns, converts {@code created_at} to UTC {@link OffsetDateTime}, scopes the
 * read to the candidate, and exposes no update/delete method (append-only,
 * {@code docs/data_model.md §6.1}).
 */
class GradeHistoryRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private GradeHistoryRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new GradeHistoryRepository(jdbc);
    }

    @Test
    void appendBindsHistoryColumnsAndReturnsGeneratedId() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(7L);

        long id = repo.append(42L, "senior", "spec-1", GradeDirection.UP, 5L, "cat-9", "promotion",
                Instant.parse("2026-02-01T10:00:00Z"));

        assertThat(id).isEqualTo(7L);
        SqlParameterSource p = capturedSourceForObject(Long.class);
        assertThat(p.getValue("candidateId")).isEqualTo(42L);
        assertThat(p.getValue("grade")).isEqualTo("senior");
        assertThat(p.getValue("specializationCode")).isEqualTo("spec-1");
        assertThat(p.getValue("direction")).isEqualTo("up");
        assertThat(p.getValue("sessionId")).isEqualTo(5L);
        assertThat(p.getValue("categoryCode")).isEqualTo("cat-9");
        assertThat(p.getValue("reason")).isEqualTo("promotion");
        assertThat(p.getValue("createdAt")).isEqualTo(OffsetDateTime.parse("2026-02-01T10:00:00Z"));
    }

    @Test
    void listByCandidateBindsCandidateId() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repo.listByCandidate(42L)).isEmpty();

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("candidateId")).isEqualTo(42L);
    }

    @Test
    void repositoryExposesNoMutationApi() {
        assertThat(GradeHistoryRepository.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("save"));
    }

    private SqlParameterSource capturedSourceForObject(Class<?> requiredType) {
        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), captor.capture(), eq(requiredType));
        return captor.getValue();
    }
}
