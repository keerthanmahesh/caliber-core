package com.caliber.service;

import com.caliber.config.AiConfig;
import com.caliber.constant.AppConstants;
import com.caliber.exception.AiConfigurationException;
import com.caliber.exception.AiExtractionException;
import com.caliber.repository.UserSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiClientServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;

    private AiConfig aiConfig;
    private ObjectMapper objectMapper;
    private AiClientService aiClientService;

    @BeforeEach
    void setUp() {
        aiConfig = new AiConfig();
        objectMapper = new ObjectMapper();
        aiClientService = new AiClientService(aiConfig, userSettingsRepository, objectMapper);
    }

    @Test
    void extractJobDetails_WhenNeitherConfigured_ThrowsAiConfigurationException() {
        when(userSettingsRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        AiConfigurationException ex = assertThrows(
                AiConfigurationException.class,
                () -> aiClientService.extractJobDetails("Senior Java Developer", "Job Description", "user-1")
        );

        assertEquals(AppConstants.ERR_AI_NOT_CONFIGURED, ex.getMessage());
    }

    @Test
    void extractJobDetails_WhenGeminiFails_ThrowsAiExtractionException() {
        when(userSettingsRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        aiConfig.getGemini().setApiKey("invalid-key");
        aiConfig.getGemini().setBaseUrl("http://localhost:9999/invalid");

        AiExtractionException ex = assertThrows(
                AiExtractionException.class,
                () -> aiClientService.extractJobDetails("Senior Java Developer", "Job Description", "user-1")
        );

        assertTrue(ex.getMessage().startsWith(AppConstants.ERR_AI_EXTRACTION_FAILED_PREFIX));
        assertNotNull(ex.getCause());
    }
}
