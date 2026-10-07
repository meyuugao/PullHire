package com.pullhire.candidate.application;

import com.pullhire.candidate.config.AppProperties;
import com.pullhire.candidate.domain.ParseStatus;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.Resume;
import com.pullhire.candidate.domain.ResumeKind;
import com.pullhire.candidate.error.ApiException;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Upload validation, rule-based parsing and {@code parse_status} transitions. */
class ResumeServiceTest {

    @TempDir
    Path tempDir;

    private ProfileRepository profiles;
    private ResumeRepository resumes;
    private CardProjectorService cards;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        resumes = mock(ResumeRepository.class);
        cards = mock(CardProjectorService.class);
        AppProperties properties = new AppProperties();
        properties.setFilesDir(tempDir.toString());
        properties.getResume().setMaxBytes(5_242_880L);
        service = new ResumeService(profiles, resumes, cards, properties);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(existingProfile()));
        when(resumes.findLatestByProfileIdAndKind(anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    void rejectsNonPdfMime() {
        MockMultipartFile file = new MockMultipartFile("file", "cv.txt", "text/plain",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("validation_error"))
                .hasMessageContaining("application/pdf");
    }

    @Test
    void rejectsMissingPdfMagic() {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "this is not a pdf".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("validation_error"))
                .hasMessageContaining("%PDF-");
    }

    @Test
    void parsesTextAndPrefillsOnlyEmptyFields() throws Exception {
        byte[] bytes = pdfWithText(
                "Ivan Petrov",
                "Email: ivan@example.com",
                "Phone: +7 999 123-45-67",
                "Senior Java developer",
                "Experience: 8 years",
                "Stack: Java, Spring, PostgreSQL");
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", bytes);

        service.upload(1L, file);

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumes).insert(resumeCaptor.capture());
        assertThat(resumeCaptor.getValue().kind()).isEqualTo(ResumeKind.UPLOADED);
        assertThat(resumeCaptor.getValue().parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(resumeCaptor.getValue().parsedAt()).isNotNull();
        assertThat(resumeCaptor.getValue().sizeBytes()).isEqualTo((long) bytes.length);

        ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).upsertByUserId(profileCaptor.capture());
        Profile merged = profileCaptor.getValue();
        assertThat(merged.contactEmail()).isEqualTo("ivan@example.com");
        assertThat(merged.contactPhone()).contains("999");
        assertThat(merged.gradeDeclared()).isEqualTo("senior");
        assertThat(merged.experienceYears()).isEqualByComparingTo("8");
        assertThat(merged.stack()).contains("Java", "Spring", "PostgreSQL");
        assertThat(merged.fullName()).isEqualTo("Ivan Petrov");

        verify(cards).rebuild(7L);
    }

    @Test
    void malformedPdfMarksFailed() {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "%PDF-1.4 this is not a working pdf".getBytes(StandardCharsets.US_ASCII));

        service.upload(1L, file);

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumes).insert(resumeCaptor.capture());
        assertThat(resumeCaptor.getValue().parseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(resumeCaptor.getValue().parsedAt()).isNotNull();

        verify(cards).rebuild(7L);
    }

    private static Profile existingProfile() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, "Ivan Petrov", null, null, null, null, null, null, null, null,
                null, null, null, "draft", now, now);
    }

    private static byte[] pdfWithText(String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setLeading(16f);
                content.beginText();
                content.newLineAtOffset(50, 780);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
