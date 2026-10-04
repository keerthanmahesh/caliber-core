package com.caliber.service;

import com.caliber.config.GmailConfig;
import com.caliber.model.JobEmail;
import com.caliber.model.ResumeDocument;
import com.caliber.repository.UserSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ThreadPreservationTest {

    @Mock
    private GmailAuthService gmailAuthService;

    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailService = new GmailService(gmailAuthService);
    }

    @Test
    void testReplyPreservesThreadHeaders() throws Exception {
        JobEmail original = JobEmail.builder()
                .messageId("msg-12345@mail.gmail.com")
                .threadId("thread-abcde")
                .senderEmail("recruiter@staffing.com")
                .subject("Immediate Need: Senior Java Engineer")
                .build();

        String replySubject = "Re: Immediate Need: Senior Java Engineer";
        String replyBody = "Hi Recruiter, is this open for C2C?";

        MimeMessage mimeMessage = gmailService.composeReplyMimeMessage(original, replySubject, replyBody, null);

        assertNotNull(mimeMessage);
        assertEquals("Re: Immediate Need: Senior Java Engineer", mimeMessage.getSubject());
        assertEquals("recruiter@staffing.com", mimeMessage.getAllRecipients()[0].toString());

        // Check Thread Preservation Headers
        assertEquals("<msg-12345@mail.gmail.com>", mimeMessage.getHeader("In-Reply-To", null));
        assertEquals("<msg-12345@mail.gmail.com>", mimeMessage.getHeader("References", null));
    }

    @Test
    void testReplyWithResumeAttachment() throws Exception {
        JobEmail original = JobEmail.builder()
                .messageId("msg-999@mail.gmail.com")
                .threadId("thread-999")
                .senderEmail("hr@enterprise.com")
                .subject("Lead Architect Position")
                .build();

        ResumeDocument resume = ResumeDocument.builder()
                .filename("Keerthan_Resume.pdf")
                .contentType("application/pdf")
                .data("Dummy PDF Bytes".getBytes(StandardCharsets.UTF_8))
                .build();

        MimeMessage mimeMessage = gmailService.composeReplyMimeMessage(
                original,
                "Lead Architect Position",
                "I am interested on C2C. Attached is my resume.",
                resume
        );

        assertNotNull(mimeMessage);
        assertTrue(mimeMessage.getSubject().startsWith("Re: "));
        assertTrue(mimeMessage.getContentType().startsWith("multipart/mixed"));
    }
}
