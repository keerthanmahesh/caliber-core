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

    @Test
    void testReplyWithHtmlAndResumeAttachment() throws Exception {
        JobEmail original = JobEmail.builder()
                .messageId("msg-888@mail.gmail.com")
                .threadId("thread-888")
                .senderEmail("hr@enterprise.com")
                .subject("Senior Java Developer")
                .build();

        ResumeDocument resume = ResumeDocument.builder()
                .filename("Keerthan_Resume.pdf")
                .contentType("application/pdf")
                .data("PDF Bytes".getBytes(StandardCharsets.UTF_8))
                .build();

        String htmlBody = "<p>Hi HR,</p><p>I am interested in the role.</p><table><tr><td>Java</td><td>10 yrs</td></tr></table>";

        MimeMessage mimeMessage = gmailService.composeReplyMimeMessage(
                original,
                "Senior Java Developer",
                htmlBody,
                resume
        );

        assertNotNull(mimeMessage);
        assertTrue(mimeMessage.getContentType().startsWith("multipart/mixed"));

        jakarta.mail.Multipart mixed = (jakarta.mail.Multipart) mimeMessage.getContent();
        assertEquals(2, mixed.getCount());

        jakarta.mail.BodyPart bodyContainer = mixed.getBodyPart(0);
        assertTrue(bodyContainer.getContentType().startsWith("multipart/alternative"));

        jakarta.mail.Multipart alt = (jakarta.mail.Multipart) bodyContainer.getContent();
        assertEquals(2, alt.getCount());
        assertTrue(alt.getBodyPart(0).getContentType().startsWith("text/plain"));
        assertTrue(alt.getBodyPart(1).getContentType().startsWith("text/html"));
    }

    @Test
    void testReplyWithHtmlWithoutAttachment() throws Exception {
        JobEmail original = JobEmail.builder()
                .messageId("msg-777@mail.gmail.com")
                .threadId("thread-777")
                .senderEmail("hr@enterprise.com")
                .subject("Java Engineer")
                .build();

        String htmlBody = "<p>Hi HR,</p><p>Thanks for reaching out.</p>";

        MimeMessage mimeMessage = gmailService.composeReplyMimeMessage(
                original,
                "Java Engineer",
                htmlBody,
                null
        );

        assertNotNull(mimeMessage);
        assertTrue(mimeMessage.getContentType().startsWith("multipart/alternative"));

        jakarta.mail.Multipart alt = (jakarta.mail.Multipart) mimeMessage.getContent();
        assertEquals(2, alt.getCount());
        assertTrue(alt.getBodyPart(0).getContentType().startsWith("text/plain"));
        assertTrue(alt.getBodyPart(1).getContentType().startsWith("text/html"));
    }
}
