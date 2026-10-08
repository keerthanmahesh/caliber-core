package com.caliber.config;

import com.caliber.model.GmailActionLabel;
import com.caliber.model.GmailEmploymentLabel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Gmail API integration and automated inbox polling.
 * All properties are bound directly from application properties with prefix 'caliber.gmail'.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "caliber.gmail")
public class GmailConfig {

    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String redirectUri;
    private long pollIntervalMs;
    private String scope;
    private String authUrl;
    private String tokenUrl;
    private long tokenExpirySeconds;
    private long maxResults;

    // Employment Type Labels in Gmail
    public String getLabelC2C() {
        return GmailEmploymentLabel.C2C.getLabelValue();
    }

    public String getLabelC2H() {
        return GmailEmploymentLabel.C2H.getLabelValue();
    }

    public String getLabelW2() {
        return GmailEmploymentLabel.W2.getLabelValue();
    }

    public String getLabelFullTime() {
        return GmailEmploymentLabel.FULL_TIME.getLabelValue();
    }

    public String getLabelUnspecified() {
        return GmailEmploymentLabel.UNSPECIFIED.getLabelValue();
    }

    // Action Labels in Gmail
    public String getLabelInquired() {
        return GmailActionLabel.INQUIRED.getLabelValue();
    }

    public String getLabelApplied() {
        return GmailActionLabel.APPLIED.getLabelValue();
    }

    public String getLabelDismissed() {
        return GmailActionLabel.DISMISSED.getLabelValue();
    }
}
