package com.caliber.service;

import com.caliber.dto.ResumeDto;
import com.caliber.model.ResumeDocument;
import com.caliber.repository.ResumeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeDocumentRepository resumeDocumentRepository;

    public ResumeDto uploadResume(String userId, MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String contentType = resolveContentType(filename, file.getContentType());

        if (!isValidResumeFormat(filename, file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file format. Please upload a PDF or Word document (.pdf, .docx, .doc)");
        }

        // Deactivate existing resumes for this user
        List<ResumeDocument> existing = resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc(userId);
        for (ResumeDocument r : existing) {
            r.setActive(false);
            resumeDocumentRepository.save(r);
        }

        String safeFilename = (filename != null && !filename.isBlank()) ? filename :
                (contentType.contains("word") || contentType.contains("officedocument") ? "Resume.docx" : "Resume.pdf");

        ResumeDocument doc = ResumeDocument.builder()
                .userId(userId)
                .filename(safeFilename)
                .contentType(contentType)
                .data(file.getBytes())
                .size(file.getSize())
                .active(true)
                .uploadedAt(Instant.now())
                .build();

        ResumeDocument saved = resumeDocumentRepository.save(doc);
        log.info("Saved new active resume '{}' ({} bytes, type='{}') for user {}",
                saved.getFilename(), saved.getSize(), saved.getContentType(), userId);

        return toDto(saved);
    }

    private String resolveContentType(String filename, String rawContentType) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".docx")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            if (lower.endsWith(".doc")) {
                return "application/msword";
            }
            if (lower.endsWith(".pdf")) {
                return "application/pdf";
            }
        }
        if (rawContentType != null && !rawContentType.isBlank() && !rawContentType.equalsIgnoreCase("application/octet-stream")) {
            return rawContentType;
        }
        return "application/pdf";
    }

    private boolean isValidResumeFormat(String filename, String contentType) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc")) {
                return true;
            }
        }
        if (contentType != null) {
            String lowerCt = contentType.toLowerCase();
            return lowerCt.contains("pdf") || lowerCt.contains("word") || lowerCt.contains("officedocument") || lowerCt.contains("msword");
        }
        return false;
    }

    public Optional<ResumeDocument> getActiveResume(String userId) {
        Optional<ResumeDocument> res = resumeDocumentRepository.findByUserIdAndActiveTrue(userId);
        if (res.isPresent()) return res;
        return resumeDocumentRepository.findFirstByActiveTrue()
                .or(resumeDocumentRepository::findFirstByOrderByUploadedAtDesc);
    }

    public Optional<ResumeDto> getActiveResumeDto(String userId) {
        return getActiveResume(userId).map(this::toDto);
    }

    public List<ResumeDto> listResumes(String userId) {
        return resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<ResumeDocument> getResumeById(String id) {
        return resumeDocumentRepository.findById(id);
    }

    public void setActiveResume(String userId, String resumeId) {
        List<ResumeDocument> resumes = resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc(userId);
        for (ResumeDocument r : resumes) {
            r.setActive(r.getId().equals(resumeId));
            resumeDocumentRepository.save(r);
        }
    }

    private ResumeDto toDto(ResumeDocument doc) {
        return ResumeDto.builder()
                .id(doc.getId())
                .filename(doc.getFilename())
                .contentType(doc.getContentType())
                .size(doc.getSize())
                .active(doc.isActive())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }
}
