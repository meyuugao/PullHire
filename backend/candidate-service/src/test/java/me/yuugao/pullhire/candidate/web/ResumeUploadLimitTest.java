package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.CardProjectorService;
import me.yuugao.pullhire.candidate.application.ResumeService;
import me.yuugao.pullhire.candidate.config.AppProperties;
import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.repo.ProfileRepository;
import me.yuugao.pullhire.candidate.repo.ResumeRepository;
import me.yuugao.pullhire.candidate.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Oversized resume upload maps to 413 problem+json {@code payload_too_large}. */
class ResumeUploadLimitTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProfileRepository profiles = mock(ProfileRepository.class);
        ResumeRepository resumes = mock(ResumeRepository.class);
        CardProjectorService cards = mock(CardProjectorService.class);
        AppProperties properties = new AppProperties();
        properties.setFilesDir(tempDir.toString());
        properties.getResume().setMaxBytes(16L);

        Profile profile = new Profile(7L, 1L, "Ivan Petrov", null, null, null, null, null, null,
                null, null, null, null, null, "draft", Instant.now(), Instant.now());
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));

        ResumeService service = new ResumeService(profiles, resumes, cards, properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ResumeController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void oversizedResumeIsPayloadTooLarge() throws Exception {
        byte[] bytes = ("%PDF-1.4 " + "x".repeat(64)).getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", bytes);

        mockMvc.perform(multipart("/candidate/resume")
                        .file(file)
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, 1L))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.code").value("payload_too_large"));
    }
}
