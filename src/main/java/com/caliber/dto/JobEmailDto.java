package com.caliber.dto;

import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEmailDto {
    private String id;
    private String messageId;
    private String threadId;
    private String senderEmail;
    private String senderName;
    private String recipientEmail;
    private String subject;
    private String snippet;
    private String bodyText;
    private String bodyHtml;
    private String jobTitle;
    private String clientOrCompany;
    private String rate;
    private String locationType;
    private String location;
    private List<String> primarySkills;
    private String summary;
    private EmploymentType employmentType;
    private ApplicationStatus applicationStatus;
    private List<String> gmailLabels;
    private Instant receivedAt;
    private Instant updatedAt;
    private Instant repliedAt;
    private String replyMessageId;
    private String lastReplyDraft;

    @Deprecated
    public EmploymentType getEmploymentStatus() {
        return employmentType;
    }
}
