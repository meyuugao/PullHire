package com.pullhire.candidate.application;

import com.pullhire.candidate.config.AppProperties;
import com.pullhire.candidate.domain.ParseStatus;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.Resume;
import com.pullhire.candidate.domain.ResumeKind;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Real PDFBox render of {@code GET /candidate/profile.pdf}. */
class PdfRenderServiceTest {

    @TempDir
    Path tempDir;

    private ProfileRepository profiles;
    private ResumeRepository resumes;
    private PdfRenderService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        resumes = mock(ResumeRepository.class);
        AppProperties properties = new AppProperties();
        properties.setFilesDir(tempDir.toString());
        service = new PdfRenderService(profiles, resumes, properties);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile()));
    }

    @Test
    void renderProducesValidNonTrivialPdfAndPersistsGeneratedFile() throws Exception {
        PdfRenderService.Rendered rendered = service.render(1L);

        assertThat(rendered.bytes().length).isGreaterThan(200);
        assertThat(new String(rendered.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(rendered.fileName()).isEqualTo("profile-7.pdf");

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumes).insert(captor.capture());
        Resume row = captor.getValue();
        assertThat(row.kind()).isEqualTo(ResumeKind.GENERATED);
        assertThat(row.mimeType()).isEqualTo("application/pdf");
        assertThat(row.parseStatus()).isEqualTo(ParseStatus.PARSED);
        assertThat(row.sizeBytes()).isEqualTo((long) rendered.bytes().length);

        Path stored = Path.of(tempDir.toString()).resolve(row.filePath());
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.size(stored)).isEqualTo(rendered.bytes().length);
    }

    @Test
    void rendersCyrillicTextVerbatim() throws Exception {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        Profile cyrillic = new Profile(8L, 2L, "Иван Петров", "candidate@example.com", null,
                "Разработчик", null, null, new String[]{"Java"}, null, null,
                "Опытный Java-разработчик", null, null, "published", now, now);
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(cyrillic));

        PdfRenderService.Rendered rendered = service.render(2L);

        assertThat(new String(rendered.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(rendered.bytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Иван");
            assertThat(text).contains("Петров");
            assertThat(text).contains("Опытный Java-разработчик");
            // F1: no '?' replacement where the Cyrillic glyphs are.
            assertThat(text).doesNotContain("?");
        }
    }

    private static Profile profile() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Profile(7L, 1L, "Ivan Petrov", "candidate@example.com", "+7 999 123-45-67",
                "Senior Java Developer", "senior", new java.math.BigDecimal("8.0"),
                new String[]{"Java", "Spring"}, new String[]{"REST"}, new String[]{"Communication"},
                "Experienced backend engineer", "backend", "it", "published", now, now);
    }
}
