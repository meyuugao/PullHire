package me.yuugao.pullhire.candidate.application;

import me.yuugao.pullhire.candidate.config.AppProperties;
import me.yuugao.pullhire.candidate.domain.ParseStatus;
import me.yuugao.pullhire.candidate.domain.Profile;
import me.yuugao.pullhire.candidate.domain.Resume;
import me.yuugao.pullhire.candidate.domain.ResumeKind;
import me.yuugao.pullhire.candidate.error.ApiException;
import me.yuugao.pullhire.candidate.repo.ProfileRepository;
import me.yuugao.pullhire.candidate.repo.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Standardized profile PDF ({@code GET /candidate/profile.pdf},
 * {@code docs/api.md §3.2}). Renders the profile with PDFBox and a bundled
 * Unicode font, persists the file in the resume volume and records a
 * {@code generated} {@code resumes} row ({@code docs/data_model.md §4.2}).
 *
 * <p>F1: the bundled {@code fonts/DejaVuSans.ttf} is loaded as a Type0 font with
 * an embedded subset, so Cyrillic (and other Unicode) text is rendered verbatim
 * rather than replaced with {@code ?}.
 *
 * <p>F9: the file is written only after a successful commit; without an active
 * transaction it is written immediately.
 */
@Service
public class PdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderService.class);

    /** Bundled Unicode font (Bitstream Vera/Arev, permissive; OQ-23). */
    private static final String FONT_RESOURCE = "/fonts/DejaVuSans.ttf";

    private final ProfileRepository profiles;
    private final ResumeRepository resumes;
    private final AppProperties appProperties;

    public PdfRenderService(ProfileRepository profiles, ResumeRepository resumes, AppProperties appProperties) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.appProperties = appProperties;
    }

    @Transactional
    public Rendered render(long userId) {
        Profile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Профиль не найден"));
        byte[] bytes = renderPdf(profile);
        String key = store(profile.id(), bytes);
        String fileName = "profile-" + profile.id() + ".pdf";
        resumes.insert(new Resume(0L, profile.id(), ResumeKind.GENERATED, fileName, key,
                "application/pdf", (long) bytes.length, ParseStatus.PARSED, Instant.now(), null));
        log.info("Сгенерирован стандартизированный PDF профиля: profile_id={}", profile.id());
        return new Rendered(bytes, fileName);
    }

    private byte[] renderPdf(Profile profile) {
        List<String> lines = new ArrayList<>();
        lines.add("PullHire - Standardized Candidate Profile");
        lines.add("");
        lines.add("Name: " + nullToDash(profile.fullName()));
        lines.add("Position: " + nullToDash(profile.positionTitle()));
        lines.add("Declared grade: " + nullToDash(profile.gradeDeclared()));
        lines.add("Experience (years): " + (profile.experienceYears() == null ? "-" : profile.experienceYears()));
        lines.add("Email: " + nullToDash(profile.contactEmail()));
        lines.add("Phone: " + nullToDash(profile.contactPhone()));
        lines.add("Specialization: " + nullToDash(profile.specializationCode()));
        lines.add("Industry: " + nullToDash(profile.industryCode()));
        lines.add("Summary: " + nullToDash(profile.summary()));
        lines.add("Stack: " + join(profile.stack()));
        lines.add("Skills: " + join(profile.skills()));
        lines.add("Soft skills: " + join(profile.softSkills()));

        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(font, 12);
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
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сгенерировать PDF профиля", ex);
        }
    }

    /** Loads the bundled Unicode font once per document as an embedded subset (F1). */
    private PDType0Font loadFont(PDDocument document) throws IOException {
        InputStream stream = getClass().getResourceAsStream(FONT_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Шрифт не найден в classpath: " + FONT_RESOURCE);
        }
        try (InputStream in = stream) {
            return PDType0Font.load(document, in, true);
        }
    }

    private String store(long profileId, byte[] bytes) {
        Path base = Path.of(appProperties.getFilesDir()).toAbsolutePath().normalize();
        String key = profileId + "/generated-" + UUID.randomUUID() + ".pdf";
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalStateException("Недопустимый путь хранения PDF");
        }
        writeAfterCommit(target, bytes);
        return key;
    }

    /** F9: never leave an orphan file behind a rolled-back DB row. */
    private void writeAfterCommit(Path target, byte[] bytes) {
        Runnable write = () -> {
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            } catch (IOException ex) {
                log.error("Не удалось записать PDF профиля: {}", target, ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) {
            return "-";
        }
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                present.add(value);
            }
        }
        return present.isEmpty() ? "-" : String.join(", ", present);
    }

    /** Rendered PDF bytes plus the download file name. */
    public record Rendered(byte[] bytes, String fileName) {
    }
}
