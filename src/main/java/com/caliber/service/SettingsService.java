package com.caliber.service;

import com.caliber.config.AiConfig;
import com.caliber.config.GmailConfig;
import com.caliber.constant.AppConstants;
import com.caliber.dto.AiExtractedJob;
import com.caliber.dto.AiTestResponse;
import com.caliber.dto.UserSettingsDto;
import com.caliber.model.JobEmail;
import com.caliber.model.UserSettings;
import com.caliber.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.caliber.constant.AppConstants.EMPTY_STRING;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String SAMPLE_AI_TEST_SUBJECT = "Urgent Requirement: Senior Java Backend Lead - C2C Open - Remote";
    private static final String SAMPLE_AI_TEST_BODY = """
            Hi Candidate,

            We have an immediate need for a Senior Java Developer with Spring Boot, AWS, Kafka. \
            Rate is $85/hr on C2C. Location is Remote. Let me know if you are interested.

            Thanks,
            Sarah Connor
            TechStaffing LLC""";

    private final UserSettingsRepository userSettingsRepository;
    private final GmailConfig gmailConfig;
    private final AiConfig aiConfig;
    private final AiClientService aiClientService;

    public UserSettings getOrCreateSettings(String userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserSettings s = UserSettings.builder()
                            .userId(userId)
                            .gmailClientId(gmailConfig.getClientId())
                            .gmailClientSecret(gmailConfig.getClientSecret())
                            .gmailRefreshToken(gmailConfig.getRefreshToken())
                            .pollIntervalMinutes((int) (gmailConfig.getPollIntervalMs() / 60000))
                            .autoArchiveProcessed(gmailConfig.isAutoArchive())
                            .updatedAt(Instant.now())
                            .build();
                    return userSettingsRepository.save(s);
                });
    }

    public UserSettingsDto getSettingsDto(String userId) {
        UserSettings s = getOrCreateSettings(userId);
        boolean isGmailConnected = (s.getGmailRefreshToken() != null && !s.getGmailRefreshToken().isBlank()) ||
                (gmailConfig.getRefreshToken() != null && !gmailConfig.getRefreshToken().isBlank());

        boolean isOllamaConfigured = (s.getOllamaApiKey() != null && !s.getOllamaApiKey().isBlank()) ||
                (aiConfig.getOllama().getApiKey() != null && !aiConfig.getOllama().getApiKey().isBlank()) ||
                (aiConfig.getOllama().getBaseUrl() != null && !aiConfig.getOllama().getBaseUrl().isBlank());

        boolean isGeminiConfigured = (s.getGeminiApiKey() != null && !s.getGeminiApiKey().isBlank()) ||
                (aiConfig.getGemini().getApiKey() != null && !aiConfig.getGemini().getApiKey().isBlank());

        return UserSettingsDto.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .gmailClientId(s.getGmailClientId() != null ? s.getGmailClientId() : gmailConfig.getClientId())
                .gmailConnected(isGmailConnected)
                .gmailSearchQuery(s.getGmailSearchQuery())
                .pollIntervalMinutes(s.getPollIntervalMinutes())
                .autoArchiveProcessed(s.isAutoArchiveProcessed())
                .inquiryTemplate(s.getInquiryTemplate())
                .applicationTemplate(s.getApplicationTemplate())
                .ollamaConfigured(isOllamaConfigured)
                .ollamaModel(aiConfig.getOllama().getModel())
                .ollamaBaseUrl(aiConfig.getOllama().getBaseUrl())
                .geminiConfigured(isGeminiConfigured)
                .geminiModel(aiConfig.getGemini().getModel())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    public UserSettingsDto updateSettings(String userId, UserSettingsDto update) {
        UserSettings s = getOrCreateSettings(userId);

        if (update.getGmailClientId() != null) s.setGmailClientId(update.getGmailClientId());
        if (update.getGmailSearchQuery() != null) s.setGmailSearchQuery(update.getGmailSearchQuery());
        if (update.getPollIntervalMinutes() != null) s.setPollIntervalMinutes(update.getPollIntervalMinutes());
        s.setAutoArchiveProcessed(update.isAutoArchiveProcessed());
        if (update.getInquiryTemplate() != null) s.setInquiryTemplate(update.getInquiryTemplate());
        if (update.getApplicationTemplate() != null) s.setApplicationTemplate(update.getApplicationTemplate());
        if (update.getOllamaApiKey() != null) s.setOllamaApiKey(update.getOllamaApiKey());

        s.setUpdatedAt(Instant.now());
        userSettingsRepository.save(s);

        return getSettingsDto(userId);
    }

    /**
     * Executes a live AI extraction test using sample job email data and current user LLM settings.
     */
    public AiTestResponse testAi(String userId) {
        try {
            AiExtractedJob result = aiClientService.extractJobDetails(SAMPLE_AI_TEST_SUBJECT, SAMPLE_AI_TEST_BODY, userId);
            return AiTestResponse.success(AppConstants.MSG_AI_TEST_SUCCEEDED, result);
        } catch (Exception e) {
            log.error("AI test failed: {}", e.getMessage(), e);
            return AiTestResponse.failure(AppConstants.ERR_AI_TEST_FAILED_PREFIX + e.getMessage());
        }
    }

    /**
     * Interpolates template placeholders: {recruiterName}, {jobTitle}, {senderName}.
     */
    public String renderTemplate(String template, JobEmail job, String senderName) {
        if (template == null) return EMPTY_STRING;
        String recruiterName = (job.getSenderName() != null && !job.getSenderName().isBlank())
                ? job.getSenderName() : "there";
        String jobTitle = (job.getJobTitle() != null && !job.getJobTitle().isBlank())
                ? job.getJobTitle() : "the job opportunity";
        String myName = (senderName != null && !senderName.isBlank()) ? senderName : "Candidate";

        return template
                .replace("{recruiterName}", recruiterName)
                .replace("{jobTitle}", jobTitle)
                .replace("{senderName}", myName);
    }
}
