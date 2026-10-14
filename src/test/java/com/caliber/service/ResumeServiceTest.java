package com.caliber.service;

import com.caliber.dto.ResumeDto;
import com.caliber.model.ResumeDocument;
import com.caliber.repository.ResumeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ResumeDocumentRepository resumeDocumentRepository;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(resumeDocumentRepository);
    }

    @Test
    void testUploadPdfResume() throws IOException {
        when(resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc("user123"))
                .thenReturn(Collections.emptyList());

        when(resumeDocumentRepository.save(any(ResumeDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Keerthan_Resume.pdf",
                "application/pdf",
                "Dummy PDF content".getBytes()
        );

        ResumeDto result = resumeService.uploadResume("user123", file);

        assertNotNull(result);
        assertEquals("Keerthan_Resume.pdf", result.getFilename());
        assertEquals("application/pdf", result.getContentType());
        assertTrue(result.isActive());

        ArgumentCaptor<ResumeDocument> captor = ArgumentCaptor.forClass(ResumeDocument.class);
        verify(resumeDocumentRepository).save(captor.capture());
        assertEquals("application/pdf", captor.getValue().getContentType());
    }

    @Test
    void testUploadDocxResume() throws IOException {
        when(resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc("user123"))
                .thenReturn(Collections.emptyList());

        when(resumeDocumentRepository.save(any(ResumeDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Keerthan_Resume.docx",
                "application/octet-stream",
                "Dummy DOCX content".getBytes()
        );

        ResumeDto result = resumeService.uploadResume("user123", file);

        assertNotNull(result);
        assertEquals("Keerthan_Resume.docx", result.getFilename());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result.getContentType());
        assertTrue(result.isActive());
    }

    @Test
    void testUploadDocResume() throws IOException {
        when(resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc("user123"))
                .thenReturn(Collections.emptyList());

        when(resumeDocumentRepository.save(any(ResumeDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Legacy_Resume.doc",
                "application/msword",
                "Dummy DOC content".getBytes()
        );

        ResumeDto result = resumeService.uploadResume("user123", file);

        assertNotNull(result);
        assertEquals("Legacy_Resume.doc", result.getFilename());
        assertEquals("application/msword", result.getContentType());
        assertTrue(result.isActive());
    }

    @Test
    void testRejectUnsupportedFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/x-msdownload",
                "Binary data".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> resumeService.uploadResume("user123", file));
        verify(resumeDocumentRepository, never()).save(any());
    }

    @Test
    void testDeleteResume_Inactive() {
        ResumeDocument doc = ResumeDocument.builder()
                .id("res-1")
                .userId("user123")
                .filename("old.pdf")
                .active(false)
                .build();

        when(resumeDocumentRepository.findById("res-1")).thenReturn(java.util.Optional.of(doc));

        resumeService.deleteResume("user123", "res-1");

        verify(resumeDocumentRepository).delete(doc);
        verify(resumeDocumentRepository, never()).findByUserIdOrderByUploadedAtDesc(any());
    }

    @Test
    void testDeleteResume_Active_PromotesNext() {
        ResumeDocument activeDoc = ResumeDocument.builder()
                .id("res-active")
                .userId("user123")
                .filename("current.pdf")
                .active(true)
                .build();

        ResumeDocument nextDoc = ResumeDocument.builder()
                .id("res-next")
                .userId("user123")
                .filename("previous.pdf")
                .active(false)
                .build();

        when(resumeDocumentRepository.findById("res-active")).thenReturn(java.util.Optional.of(activeDoc));
        when(resumeDocumentRepository.findByUserIdOrderByUploadedAtDesc("user123"))
                .thenReturn(java.util.List.of(nextDoc));

        resumeService.deleteResume("user123", "res-active");

        verify(resumeDocumentRepository).delete(activeDoc);
        assertTrue(nextDoc.isActive());
        verify(resumeDocumentRepository).save(nextDoc);
    }

    @Test
    void testDeleteResume_ForbiddenUser() {
        ResumeDocument doc = ResumeDocument.builder()
                .id("res-1")
                .userId("other-user")
                .filename("secret.pdf")
                .build();

        when(resumeDocumentRepository.findById("res-1")).thenReturn(java.util.Optional.of(doc));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> resumeService.deleteResume("user123", "res-1"));
        verify(resumeDocumentRepository, never()).delete(any());
    }

    @Test
    void testDeleteResume_NotFound() {
        when(resumeDocumentRepository.findById("res-none")).thenReturn(java.util.Optional.empty());

        resumeService.deleteResume("user123", "res-none");

        verify(resumeDocumentRepository, never()).delete(any());
    }
}

