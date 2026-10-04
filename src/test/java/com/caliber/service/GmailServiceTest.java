package com.caliber.service;

import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GmailServiceTest {

    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailService = new GmailService(null);
    }

    @Test
    void testStripHtmlWithCssAndStyleTags() {
        String html = "<!DOCTYPE html><html><head>" +
                "<style type=\"text/css\">\n" +
                "@import url('theme.css');\n" +
                "body{font-size:14px;-webkit-text-size-adjust:100%; -ms-text-size-adjust:100%;font-family:'Open Sans', sans-serif;}\n" +
                ".atsEmail{max-width: 100%;padding:10px;color:#333;}\n" +
                ".tableBlock table{border-collapse: collapse;}\n" +
                "@media screen and (max-width: 600px) { .boxColM{max-width: 100%!important;} }\n" +
                "</style></head>" +
                "<body>" +
                "<p>Hello Candidate,</p>" +
                "<p>We have an urgent opening for <b>Senior Java Engineer</b>.</p>" +
                "<ul><li>Experience: 8+ years</li><li>Location: Remote (US Only)</li><li>Rate: $85/hr C2C</li></ul>" +
                "<p>Best regards,<br>Recruiter Team</p>" +
                "</body></html>";

        String plainText = gmailService.stripHtml(html);

        assertNotNull(plainText);
        assertFalse(plainText.contains("@import"));
        assertFalse(plainText.contains("font-size"));
        assertFalse(plainText.contains(".atsEmail"));
        assertFalse(plainText.contains("@media"));
        assertTrue(plainText.contains("Hello Candidate"));
        assertTrue(plainText.contains("Senior Java Engineer"));
        assertTrue(plainText.contains("Rate: $85/hr C2C"));
        assertTrue(plainText.contains("Recruiter Team"));
    }

    @Test
    void testCleanPlainTextWithResidualCss() {
        String rawWithCss = "Your Email Title @import url('theme.css'); " +
                "body{font-size:14px;-webkit-text-size-adjust:100%; -ms-text-size-adjust:100%;font-family:'Open Sans', sans-serif, Arial, sans-serif;} " +
                ".atsEmail{max-width: 100%;padding:10px;font-family:'Open Sans', sans-serif, Arial, sans-serif; color:#333;} " +
                ".atsMBlock *{box-sizing: border-box;} .atsEmail p{font-size:14px;} " +
                ".tableBlock table{border-collapse: collapse;} .tableBlock th{ white-space: nowrap;} " +
                "@media screen and (max-width: 600px) { .boxColM{max-width: 100%!important;} } " +
                "We are hiring a Lead Cloud Architect in New York. Rate is $95/hr on C2C.";

        String cleaned = gmailService.cleanPlainText(rawWithCss);

        assertNotNull(cleaned);
        assertFalse(cleaned.contains("@import"));
        assertFalse(cleaned.contains("font-size"));
        assertFalse(cleaned.contains(".atsEmail"));
        assertFalse(cleaned.contains("@media"));
        assertTrue(cleaned.contains("Lead Cloud Architect"));
        assertTrue(cleaned.contains("Rate is $95/hr on C2C"));
    }
}
