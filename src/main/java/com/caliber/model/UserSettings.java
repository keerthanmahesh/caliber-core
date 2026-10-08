package com.caliber.model;

import com.caliber.constant.AppConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * User-level settings for templates, Gmail sync configuration, and AI options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = AppConstants.COLLECTION_USER_SETTING)
public class UserSettings {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    // Gmail API Credentials / Tokens
    private String gmailClientId;
    private String gmailClientSecret;
    private String gmailRefreshToken;
    private String gmailAccessToken;
    private Long tokenExpiresAt;
    @Builder.Default
    private boolean gmailConnected = false;

    // Ingestion query entered by user
    private String gmailSearchQuery;

    @Builder.Default
    private Integer pollIntervalHours = 1;

    private Integer pollIntervalMinutes;

    public Integer getEffectivePollIntervalHours() {
        if (pollIntervalHours != null && pollIntervalHours > 0) {
            return pollIntervalHours;
        }
        if (pollIntervalMinutes != null && pollIntervalMinutes > 0) {
            return Math.max(1, Math.round(pollIntervalMinutes / 60.0f));
        }
        return null;
    }

    @Builder.Default
    private boolean autoArchiveProcessed = true;

    // Reply Templates
    @Builder.Default
    private String inquiryTemplate = "Hi {recruiterName},\n\nThanks for reaching out regarding the {jobTitle} role. Is this position open on a Corp-to-Corp (C2C) basis? If so, I'd be very interested in discussing further details.\n\nBest regards,\n{senderName}";

    @Builder.Default
    private String applicationTemplate = "Hi {recruiterName},\n\nThanks for reaching out. I am interested in this {jobTitle} position on a C2C basis. Attached is my latest resume for your review.\n\nPlease let me know the best time to connect for next steps.\n\nBest regards,\n{senderName}";

    // AI Provider Configuration overrides (optional)
    private String ollamaApiKey;
    private String groqApiKey;
    private String geminiApiKey;

    private Instant lastSyncedAt;
    private Instant updatedAt;
}
