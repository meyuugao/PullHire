package com.pullhire.candidate.application;

import com.pullhire.candidate.config.AppProperties;
import com.pullhire.candidate.domain.ParseStatus;
import com.pullhire.candidate.domain.Profile;
import com.pullhire.candidate.domain.Resume;
import com.pullhire.candidate.domain.ResumeKind;
import com.pullhire.candidate.error.ApiException;
import com.pullhire.candidate.repo.ProfileRepository;
import com.pullhire.candidate.repo.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uploaded PDF resume handling ({@code docs/api.md §3.2},
 * {@code docs/data_model.md §4.2}). Validates the file, stores it in the resume
 * volume (metadata only in the DB), extracts text with PDFBox and pre-fills only
 * the profile fields that are still empty ("Поля предзаполнены, кандидат правит").
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE =
            Pattern.compile("\\+?[0-9][0-9\\s().-]{7,}[0-9]");
    private static final Pattern GRADE =
            Pattern.compile("(?i)\\b(intern|junior|middle|senior|lead|principal)\\b");
    private static final Pattern EXPERIENCE =
            Pattern.compile("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*(?:лет|года|год|years?|yrs?)");
    private static final List<String> STACK_TOKENS = List.of(
            "Java", "Kotlin", "Spring", "Spring Boot", "PostgreSQL", "MySQL", "MongoDB", "Redis",
            "Python", "JavaScript", "TypeScript", "React", "Node.js", "Go", "C#", ".NET",
            "Docker", "Kubernetes", "RabbitMQ", "Kafka", "REST", "SQL", "Hibernate", "Maven",
            "Gradle", "Linux", "AWS", "Git");

    private final ProfileRepository profiles;
    private final ResumeRepository resumes;
    private final CardProjectorService cards;
    private final AppProperties appProperties;

    public ResumeService(ProfileRepository profiles, ResumeRepository resumes, CardProjectorService cards,
                         AppProperties appProperties) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.cards = cards;
        this.appProperties = appProperties;
    }

    /** Validates + stores an uploaded resume, parses it, pre-fills gaps and re-projects the card. */
    @Transactional
    public Resume upload(long userId, MultipartFile file) {
        Profile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Профиль не найден"));

        if (file == null || file.isEmpty()) {
            throw ApiException.validation("Файл резюме обязателен");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("application/pdf")) {
            throw ApiException.validation("Ожидается PDF (application/pdf)");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.validation("Не удалось прочитать файл резюме");
        }
        if (!hasPdfMagic(bytes)) {
            throw ApiException.validation("Файл не является PDF (ожидается сигнатура %PDF-)");
        }
        long maxBytes = appProperties.getResume().getMaxBytes();
        if (bytes.length > maxBytes) {
            throw ApiException.payloadTooLarge("Резюме превышает лимит " + maxBytes + " байт");
        }

        String key = store(profile.id(), bytes);
        Parsed parsed = parse(bytes);
        if (parsed.success()) {
            prefill(profile, parsed);
        }
        ParseStatus status = parsed.success() ? ParseStatus.PARSED : ParseStatus.FAILED;
        Resume resume = new Resume(0L, profile.id(), ResumeKind.UPLOADED, safeName(file.getOriginalFilename()),
                key, contentType, (long) bytes.length, status, Instant.now(), null);
        long id = resumes.insert(resume);
        cards.rebuild(profile.id());
        log.info("Резюме загружено: profile_id={}, parse_status={}", profile.id(), status.wire());
        return resumes.findLatestByProfileIdAndKind(profile.id(), ResumeKind.UPLOADED).orElse(resume);
    }

    /** Pre-fills only null/empty profile fields from the parsed resume text. */
    private void prefill(Profile profile, Parsed parsed) {
        String phone = firstNonBlank(profile.contactPhone(), parsed.phone());
        String email = firstNonBlank(profile.contactEmail(), parsed.email());
        String grade = firstNonBlank(profile.gradeDeclared(), parsed.grade());
        BigDecimal years = profile.experienceYears() != null ? profile.experienceYears() : parsed.experienceYears();
        String[] stack = profile.stack() != null && profile.stack().length > 0
                ? profile.stack()
                : parsed.stack().toArray(String[]::new);
        if (phone == null && email == null && grade == null && years == null
                && (stack == null || stack.length == 0)) {
            return;
        }
        Profile merged = new Profile(profile.id(), profile.userId(), profile.fullName(), email, phone,
                profile.positionTitle(), grade, years, stack, profile.skills(), profile.softSkills(),
                profile.summary(), profile.specializationCode(), profile.industryCode(),
                profile.status() != null ? profile.status() : "published",
                profile.createdAt(), Instant.now());
        profiles.upsertByUserId(merged);
    }

    private Parsed parse(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            return Parsed.success(text);
        } catch (IOException | RuntimeException ex) {
            log.warn("Не удалось распознать PDF-резюме: {}", ex.getMessage());
            return Parsed.failure();
        }
    }

    private String store(long profileId, byte[] bytes) {
        Path base = Path.of(appProperties.getFilesDir()).toAbsolutePath().normalize();
        String key = profileId + "/" + UUID.randomUUID() + ".pdf";
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalStateException("Недопустимый путь хранения резюме");
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
                log.error("Не удалось записать файл резюме: {}", target, ex);
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

    private static boolean hasPdfMagic(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static String safeName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "resume.pdf";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static String firstNonBlank(String existing, String parsed) {
        return (existing == null || existing.isBlank()) ? parsed : existing;
    }

    /** Rule-based extraction result from the resume text. */
    static final class Parsed {

        private final boolean success;
        private final String email;
        private final String phone;
        private final String grade;
        private final BigDecimal experienceYears;
        private final List<String> stack;

        private Parsed(boolean success, String email, String phone, String grade,
                       BigDecimal experienceYears, List<String> stack) {
            this.success = success;
            this.email = email;
            this.phone = phone;
            this.grade = grade;
            this.experienceYears = experienceYears;
            this.stack = stack;
        }

        static Parsed success(String text) {
            return new Parsed(true, match(EMAIL, text), match(PHONE, text), grade(text), years(text), stack(text));
        }

        static Parsed failure() {
            return new Parsed(false, null, null, null, null, List.of());
        }

        boolean success() {
            return success;
        }

        String email() {
            return email;
        }

        String phone() {
            return phone;
        }

        String grade() {
            return grade;
        }

        BigDecimal experienceYears() {
            return experienceYears;
        }

        List<String> stack() {
            return stack;
        }

        private static String match(Pattern pattern, String text) {
            Matcher matcher = pattern.matcher(text);
            return matcher.find() ? matcher.group().trim() : null;
        }

        private static String grade(String text) {
            Matcher matcher = GRADE.matcher(text);
            return matcher.find() ? matcher.group().toLowerCase(Locale.ROOT) : null;
        }

        private static BigDecimal years(String text) {
            Matcher matcher = EXPERIENCE.matcher(text);
            if (!matcher.find()) {
                return null;
            }
            try {
                return new BigDecimal(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static List<String> stack(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            List<String> found = new ArrayList<>();
            for (String token : STACK_TOKENS) {
                if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                    found.add(token);
                }
            }
            return found;
        }
    }
}
