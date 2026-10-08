package me.yuugao.pullhire.assessment.repo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
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
 * Pure unit checks (no DB) that {@link TestSessionRepository} strips the session
 * status to its wire value, converts timestamps to UTC {@link OffsetDateTime},
 * binds the jsonb survey answers and the {@code text[]} declared dimensions, and
 * names every SQL parameter correctly.
 */
class TestSessionRepositoryTest {

    private NamedParameterJdbcTemplate jdbc;
    private TestSessionRepository repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        repo = new TestSessionRepository(jdbc);
    }

    @Test
    void createBindsSessionColumnsAndReturnsGeneratedId() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(5L);

        long id = repo.create(session(0L));

        assertThat(id).isEqualTo(5L);
        SqlParameterSource p = capturedSourceForObject(Long.class);
        assertThat(p.getValue("candidateId")).isEqualTo(42L);
        assertThat(p.getValue("industryCode")).isEqualTo("industry-1");
        assertThat(p.getValue("specializationCode")).isEqualTo("spec-1");
        assertThat(p.getValue("targetGrade")).isEqualTo("middle");
        assertThat(p.getValue("surveyAnswers")).isEqualTo("{\"q\":1}");
        assertThat(p.getValue("declaredDimensions")).isInstanceOf(SqlTypeValue.class);
        assertThat(p.getValue("formSeed")).isEqualTo(777L);
        assertThat(p.getValue("status")).isEqualTo("started");
        assertThat(p.getValue("theta")).isEqualTo(new BigDecimal("-0.250"));
        assertThat(p.getValue("thetaSe")).isEqualTo(new BigDecimal("0.500"));
        assertThat(p.getValue("itemCount")).isEqualTo(3);
        assertThat(p.getValue("startedAt")).isEqualTo(OffsetDateTime.parse("2026-02-01T10:00:00Z"));
        assertThat(p.getValue("completedAt")).isNull();
    }

    @Test
    void createBindsNullDeclaredDimensionsWhenAbsent() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);

        TestSession noDimensions = new TestSession(0L, 42L, null, "spec-1", "middle", null, null, null,
                SessionStatus.STARTED, null, null, 0, null, null);
        repo.create(noDimensions);

        SqlParameterSource p = capturedSourceForObject(Long.class);
        assertThat(p.getValue("declaredDimensions")).isNull();
        assertThat(p.getValue("startedAt")).isNull();
        assertThat(p.getValue("surveyAnswers")).isNull();
    }

    @Test
    void findByIdBindsId() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repo.findById(9L)).isEmpty();

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("id")).isEqualTo(9L);
    }

    @Test
    void updateBindsProgressColumnsAndUtcCompletedAt() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        TestSession completed = new TestSession(5L, 42L, null, "spec-1", "middle", null, null, 777L,
                SessionStatus.COMPLETED, new BigDecimal("0.125"), new BigDecimal("0.300"), 12,
                Instant.parse("2026-02-01T10:00:00Z"), Instant.parse("2026-02-01T10:30:00Z"));

        assertThat(repo.update(completed)).isEqualTo(1);
        SqlParameterSource p = capturedSourceForUpdate();
        assertThat(p.getValue("id")).isEqualTo(5L);
        assertThat(p.getValue("status")).isEqualTo("completed");
        assertThat(p.getValue("theta")).isEqualTo(new BigDecimal("0.125"));
        assertThat(p.getValue("thetaSe")).isEqualTo(new BigDecimal("0.300"));
        assertThat(p.getValue("itemCount")).isEqualTo(12);
        assertThat(p.getValue("formSeed")).isEqualTo(777L);
        assertThat(p.getValue("completedAt")).isEqualTo(OffsetDateTime.parse("2026-02-01T10:30:00Z"));
    }

    @Test
    void updatePersistsTargetGrade() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        TestSession graded = new TestSession(5L, 42L, null, "spec-1", "senior", null, null, 777L,
                SessionStatus.STARTED, new BigDecimal("-0.250"), new BigDecimal("0.500"), 0,
                Instant.parse("2026-02-01T10:00:00Z"), null);

        assertThat(repo.update(graded)).isEqualTo(1);
        SqlParameterSource p = capturedSourceForUpdate();
        assertThat(p.getValue("targetGrade")).isEqualTo("senior");
        assertThat(p.getValue("status")).isEqualTo("started");
    }

    @Test
    void updateStatementWritesTargetGradeColumn() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        repo.update(session(5L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getValue()).contains("target_grade");
    }

    @Test
    void findByCandidateBindsCandidateId() {
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repo.findByCandidate(42L)).isEmpty();

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("candidateId")).isEqualTo(42L);
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

    private static TestSession session(long id) {
        return new TestSession(id, 42L, "industry-1", "spec-1", "middle", "{\"q\":1}",
                List.of("seniority", "industry"), 777L, SessionStatus.STARTED,
                new BigDecimal("-0.250"), new BigDecimal("0.500"), 3,
                Instant.parse("2026-02-01T10:00:00Z"), null);
    }
}
