package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.PdfRenderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /candidate/profile.pdf} ({@code docs/api.md §3.2}). */
@RestController
public class ProfilePdfController {

    private final PdfRenderService pdf;

    public ProfilePdfController(PdfRenderService pdf) {
        this.pdf = pdf;
    }

    @GetMapping("/candidate/profile.pdf")
    public ResponseEntity<byte[]> download(HttpServletRequest request) {
        PdfRenderService.Rendered rendered = pdf.render(CurrentUser.id(request));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.fileName() + "\"")
                .body(rendered.bytes());
    }
}
