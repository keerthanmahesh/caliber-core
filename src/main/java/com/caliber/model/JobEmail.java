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
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document representing an ingested and parsed recruiter job email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = AppConstants.COLLECTION_JOB_EMAIL)
public class JobEmail {

    @Id
    private String id;

    @Indexed(unique = true)
    private String messageId;

    @Indexed
    private String threadId;

    private String senderEmail;
    private String senderName;
    private String recipientEmail;
    private String subject;
    private String snippet;
    private String bodyText;
    private String bodyHtml;

    // Structured fields extracted via Classification Engine & LLM
    private String jobTitle;
    private String clientOrCompany;
    private String company;
    private String client;
    private String rate;
    private String locationType; // Remote | Hybrid | Onsite | Unspecified
    private String location;

    @Builder.Default
    private List<String> primarySkills = new ArrayList<>();

    private String summary;

    @Indexed
    private EmploymentType employmentType;

    @Indexed
    @Builder.Default
    private ApplicationStatus applicationStatus = ApplicationStatus.PENDING;

    @Builder.Default
    private List<String> gmailLabels = new ArrayList<>();

    private Instant receivedAt;
    private Instant updatedAt;
    private Instant repliedAt;
    private String replyMessageId;
    private String lastReplyDraft;

    @Indexed
    private String userId;
}
