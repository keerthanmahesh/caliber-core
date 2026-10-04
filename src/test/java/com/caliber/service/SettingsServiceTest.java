package com.caliber.service;

import com.caliber.config.AiConfig;
import com.caliber.config.GmailConfig;
import com.caliber.constant.AppConstants;
import com.caliber.dto.AiExtractedJob;
import com.caliber.dto.AiTestResponse;
import com.caliber.model.JobEmail;
import com.caliber.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private GmailConfig gmailConfig;

    @Mock
    private AiConfig aiConfig;

    @Mock
    private AiClientService aiClientService;

    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new SettingsService(
                userSettingsRepository,
                gmailConfig,
                aiConfig,
                aiClientService
        );
    }

    @Test
    void testAi_WhenAiClientSucceeds_ReturnsSuccessResponse() {
        AiExtractedJob mockResult = AiExtractedJob.builder()
                .jobTitle("Senior Java Developer")
                .employmentType("C2C")
                .c2cStatus("C2C")
                .build();

        when(aiClientService.extractJobDetails(anyString(), anyString(), eq("user-1"))).thenReturn(mockResult);

        AiTestResponse response = settingsService.testAi("user-1");

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(AppConstants.MSG_AI_TEST_SUCCEEDED, response.message());
        assertEquals(mockResult, response.data());
        verify(aiClientService).extractJobDetails(anyString(), anyString(), eq("user-1"));
    }

    @Test
    void testAi_WhenAiClientFails_ReturnsFailureResponse() {
        when(aiClientService.extractJobDetails(anyString(), anyString(), eq("user-1")))
                .thenThrow(new RuntimeException("Connection timed out"));

        AiTestResponse response = settingsService.testAi("user-1");

        assertNotNull(response);
        assertFalse(response.success());
        assertTrue(response.message().contains("Connection timed out"));
        assertNull(response.data());
    }

    @Test
    void renderTemplate_ReplacesPlaceholders() {
        JobEmail job = JobEmail.builder()
                .senderName("Alice")
                .jobTitle("Lead Architect")
                .build();

        String template = "Hi {recruiterName}, I am interested in {jobTitle}. Regards, {senderName}";
        String rendered = settingsService.renderTemplate(template, job, "Bob");

        assertEquals("Hi Alice, I am interested in Lead Architect. Regards, Bob", rendered);
    }
}
