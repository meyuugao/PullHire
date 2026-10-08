package me.yuugao.pullhire.assessment.application;

import me.yuugao.pullhire.assessment.config.AssessmentProperties;
import me.yuugao.pullhire.assessment.domain.SessionStatus;
import me.yuugao.pullhire.assessment.domain.TestSession;
import me.yuugao.pullhire.assessment.error.ApiException;
import me.yuugao.pullhire.assessment.repo.ItemBankRepository;
import me.yuugao.pullhire.assessment.repo.TestSessionRepository;
import me.yuugao.pullhire.assessment.web.dto.SurveyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit checks (no Spring, no DB) for {@link SurveyService}: it opens a
 * {@code started} session for the candidate, serializes the survey answers to the
 * jsonb column, binds the declared dimensions, and returns the generated id.
 * Taxonomy codes are validated against the seeded item bank and configured grades.
 */
class SurveyServiceTest {

    private TestSessionRepository sessions;
    private ItemBankRepository itemBank;
    private SurveyService service;

    @BeforeEach
    void setUp() {
        sessions = mock(TestSessionRepository.class);
        itemBank = mock(ItemBankRepository.class);
        when(itemBank.listDistinctSpecializations()).thenReturn(List.of("spec-1"));
        service = new SurveyService(sessions, itemBank, new AssessmentProperties());
    }

    @Test
    void writesSurveyFieldsOntoANewSessionAndReturnsItsId() {
        when(sessions.create(any(TestSession.class))).thenReturn(77L);
        SurveyRequest request = new SurveyRequest("industry-1", "spec-1", "middle",
                List.of("spec-1", "middle", "industry-1"), Map.of("q1", "a"));

        long id = service.submit(42L, request);

        assertThat(id).isEqualTo(77L);
        TestSession saved = capture();
        assertThat(saved.id()).isZero();
        assertThat(saved.candidateId()).isEqualTo(42L);
        assertThat(saved.industryCode()).isEqualTo("industry-1");
        assertThat(saved.specializationCode()).isEqualTo("spec-1");
        assertThat(saved.targetGrade()).isEqualTo("middle");
        assertThat(saved.status()).isEqualTo(SessionStatus.STARTED);
        assertThat(saved.declaredDimensions()).containsExactly("spec-1", "middle", "industry-1");
        assertThat(saved.surveyAnswers()).isEqualTo("{\"q1\":\"a\"}");
        assertThat(saved.formSeed()).isNull();
        assertThat(saved.theta()).isNull();
        assertThat(saved.thetaSe()).isNull();
        assertThat(saved.itemCount()).isZero();
        assertThat(saved.startedAt()).isNull();
        assertThat(saved.completedAt()).isNull();
    }

    @Test
    void trimsCodesAndDropsNullDimensions() {
        when(sessions.create(any(TestSession.class))).thenReturn(1L);

        service.submit(42L, new SurveyRequest("  industry-1  ", "  spec-1  ", "  middle  ",
                Arrays.asList("spec-1", null, " middle "), Map.of("q", 1)));

        TestSession saved = capture();
        assertThat(saved.industryCode()).isEqualTo("industry-1");
        assertThat(saved.specializationCode()).isEqualTo("spec-1");
        assertThat(saved.targetGrade()).isEqualTo("middle");
        assertThat(saved.declaredDimensions()).containsExactly("spec-1", "middle");
    }

    @Test
    void leavesIndustryNullWhenAbsent() {
        when(sessions.create(any(TestSession.class))).thenReturn(1L);

        service.submit(42L, new SurveyRequest(null, "spec-1", "middle", List.of("x"), Map.of("q", 1)));

        assertThat(capture().industryCode()).isNull();
    }

    @Test
    void issuesOnlyASingleInsertForTheCandidate() {
        when(sessions.create(any(TestSession.class))).thenReturn(5L);

        service.submit(42L, new SurveyRequest(null, "spec-1", "middle", List.of("x"), Map.of("q", 1)));

        verify(sessions).create(any(TestSession.class));
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void unknownSpecializationIsRejected() {
        SurveyRequest request = new SurveyRequest(null, "nope", "middle",
                List.of("middle"), Map.of("q", 1));

        assertThatThrownBy(() -> service.submit(42L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("validation_error"));
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void unknownGradeIsRejected() {
        SurveyRequest request = new SurveyRequest(null, "spec-1", "staff++",
                List.of("spec-1"), Map.of("q", 1));

        assertThatThrownBy(() -> service.submit(42L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("validation_error"));
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void blankDimensionIsRejected() {
        SurveyRequest request = new SurveyRequest(null, "spec-1", "middle",
                List.of("   "), Map.of("q", 1));

        assertThatThrownBy(() -> service.submit(42L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("validation_error"));
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void industryDimensionIsAcceptedWithoutKnownList() {
        when(sessions.create(any(TestSession.class))).thenReturn(1L);

        service.submit(42L, new SurveyRequest(null, "spec-1", "middle",
                List.of("fintech"), Map.of("q", 1)));

        assertThat(capture().declaredDimensions()).containsExactly("fintech");
    }

    @Test
    void unknownNonBlankDimensionIsAcceptedAsIndustryCode() {
        when(sessions.create(any(TestSession.class))).thenReturn(1L);

        service.submit(42L, new SurveyRequest(null, "spec-1", "middle",
                List.of("spec-1", "middle", "some-new-vertical"), Map.of("q", 1)));

        assertThat(capture().declaredDimensions())
                .containsExactly("spec-1", "middle", "some-new-vertical");
    }

    private TestSession capture() {
        ArgumentCaptor<TestSession> captor = ArgumentCaptor.forClass(TestSession.class);
        verify(sessions).create(captor.capture());
        return captor.getValue();
    }
}
