package com.caliber.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDto {
    private String id;
    private String userId;
    private String gmailClientId;
    private boolean gmailConnected;
    private String gmailSearchQuery;
    private Integer pollIntervalHours;
    private Integer pollIntervalMinutes;
    private boolean autoArchiveProcessed;
    private String inquiryTemplate;
    private String applicationTemplate;
    private boolean ollamaConfigured;
    private String ollamaModel;
    private String ollamaBaseUrl;
    private String ollamaApiKey;
    private boolean groqConfigured;
    private String groqModel;
    private boolean geminiConfigured;
    private String geminiModel;
    private Instant lastSyncedAt;
    private Instant updatedAt;
}
