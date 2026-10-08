package me.yuugao.pullhire.candidate.web;

import me.yuugao.pullhire.candidate.application.ResumeService;
import me.yuugao.pullhire.candidate.web.dto.ResumeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** {@code POST /candidate/resume} ({@code docs/api.md §3.2}). */
@RestController
public class ResumeController {

    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    @PostMapping("/candidate/resume")
    public ResponseEntity<ResumeResponse> upload(@RequestParam("file") MultipartFile file,
                                                 HttpServletRequest request) {
        ResumeResponse response = ResumeResponse.from(resumes.upload(CurrentUser.id(request), file));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
