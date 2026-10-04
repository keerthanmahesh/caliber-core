package com.caliber.controller;

import com.caliber.dto.ResumeDto;
import com.caliber.model.ResumeDocument;
import com.caliber.service.ResumeService;
import com.caliber.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final UserService userService;

    private String resolveUserId() {
        return userService.resolveCurrentUserId();
    }

    @PostMapping("/upload")
    public ResponseEntity<ResumeDto> uploadResume(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload empty file");
        }
        String userId = resolveUserId();
        ResumeDto uploaded = resumeService.uploadResume(userId, file);
        return ResponseEntity.ok(uploaded);
    }

    @GetMapping("/active")
    public ResponseEntity<ResumeDto> getActiveResume() {
        String userId = resolveUserId();
        return resumeService.getActiveResumeDto(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<List<ResumeDto>> listResumes() {
        String userId = resolveUserId();
        return ResponseEntity.ok(resumeService.listResumes(userId));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateResume(@PathVariable String id) {
        String userId = resolveUserId();
        resumeService.setActiveResume(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadResume(@PathVariable String id) {
        ResumeDocument doc = resumeService.getResumeById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType() != null ? doc.getContentType() : "application/pdf"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .body(doc.getData());
    }
}
