package com.caliber.service;

import com.caliber.constant.AppConstants;
import com.caliber.dto.JobEmailDto;
import com.caliber.dto.JobReplyDraftDto;
import com.caliber.dto.JobStatsDto;
import com.caliber.dto.SendReplyRequest;
import com.caliber.dto.SyncResultDto;
import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import com.caliber.model.GmailActionLabel;
import com.caliber.model.GmailEmploymentLabel;
import com.caliber.model.JobEmail;
import com.caliber.model.ResumeDocument;
import com.caliber.model.UserSettings;
import com.caliber.repository.JobEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service managing job email lifecycle, querying, Gmail synchronization, draft generation,
 * label reconciliation, and statistics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobEmailService {

    private final JobEmailRepository jobEmailRepository;
    private final IngestionService ingestionService;
    private final GmailService gmailService;
    private final GmailAuthService gmailAuthService;
    private final ResumeService resumeService;
    private final SettingsService settingsService;

    /**
     * Lists and filters job emails based on tab, status, and search query.
     */
    public Page<JobEmailDto> listJobs(String tab, String status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, AppConstants.FIELD_RECEIVED_AT));
        Page<JobEmail> emailPage = findMatchingEmails(tab, status, search, pageable);
        return emailPage.map(this::toDto);
    }

    private Page<JobEmail> findMatchingEmails(String tab, String status, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return jobEmailRepository.searchAll(search.trim(), pageable);
        }
        return findByTab(tab, status, pageable);
    }

    private Page<JobEmail> findByTab(String tab, String status, Pageable pageable) {
        if (tab == null || tab.isBlank()) {
            return jobEmailRepository.findAll(pageable);
        }

        String normalizedTab = tab.trim().toLowerCase();
        return switch (normalizedTab) {
            case AppConstants.TAB_C2C ->
                    jobEmailRepository.findByEmploymentTypeAndApplicationStatusNotIn(EmploymentType.C2C, AppConstants.ACTED_STATUSES, pageable);
            case AppConstants.TAB_C2H ->
                    jobEmailRepository.findByEmploymentTypeAndApplicationStatusNotIn(EmploymentType.C2H, AppConstants.ACTED_STATUSES, pageable);
            case AppConstants.TAB_W2 ->
                    jobEmailRepository.findByEmploymentTypeAndApplicationStatusNotIn(EmploymentType.W2, AppConstants.ACTED_STATUSES, pageable);
            case AppConstants.TAB_FULL_TIME ->
                    jobEmailRepository.findByEmploymentTypeAndApplicationStatusNotIn(EmploymentType.FULL_TIME, AppConstants.ACTED_STATUSES, pageable);
            case AppConstants.TAB_UNSPECIFIED ->
                    jobEmailRepository.findUnspecifiedAndApplicationStatusNotIn(AppConstants.ACTED_STATUSES, pageable);
            case AppConstants.TAB_HISTORY, AppConstants.TAB_ACTED ->
                    findHistoryByStatus(status, pageable);
            default ->
                    jobEmailRepository.findAll(pageable);
        };
    }

    private Page<JobEmail> findHistoryByStatus(String status, Pageable pageable) {
        if (status == null || status.isBlank() || AppConstants.STATUS_ACTED.equalsIgnoreCase(status)) {
            return jobEmailRepository.findByApplicationStatusIn(
                    AppConstants.ACTED_STATUSES,
                    pageable
            );
        }
        if (AppConstants.STATUS_ALL.equalsIgnoreCase(status)) {
            return jobEmailRepository.findAll(pageable);
        }
        try {
            ApplicationStatus appStatus = ApplicationStatus.valueOf(status.trim().toUpperCase());
            return jobEmailRepository.findByApplicationStatus(appStatus, pageable);
        } catch (IllegalArgumentException e) {
            return jobEmailRepository.findAll(pageable);
        }
    }

    /**
     * Retrieves a single job email entity by ID.
     */
    public JobEmail findJobEmailOrThrow(String id) {
        return jobEmailRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, AppConstants.ERR_JOB_EMAIL_NOT_FOUND));
    }

    /**
     * Retrieves single job email DTO by ID.
     */
    public JobEmailDto getJobById(String id) {
        return toDto(findJobEmailOrThrow(id));
    }

    /**
     * Pre-generates reply draft for a given job based on inquiry vs application template.
     */
    public JobReplyDraftDto getReplyDraft(String id, String type, String userId) {
        JobEmail email = findJobEmailOrThrow(id);
        UserSettings settings = settingsService.getOrCreateSettings(userId);

        boolean isApplication = isApplicationDraft(type, email.getEmploymentType());
        String template = isApplication ? settings.getApplicationTemplate() : settings.getInquiryTemplate();
        String renderedBody = settingsService.renderTemplate(template, email, AppConstants.DEFAULT_CANDIDATE_NAME);
        String subject = formatReplySubject(email.getSubject());

        return JobReplyDraftDto.builder()
                .subject(subject)
                .recipientEmail(email.getSenderEmail() != null ? email.getSenderEmail() : AppConstants.EMPTY_STRING)
                .recipientName(email.getSenderName() != null ? email.getSenderName() : AppConstants.EMPTY_STRING)
                .body(renderedBody)
                .defaultAttachResume(isApplication)
                .build();
    }

    private boolean isApplicationDraft(String type, EmploymentType employmentType) {
        return AppConstants.REPLY_TYPE_APPLY.equalsIgnoreCase(type) ||
                (type == null && employmentType == EmploymentType.C2C);
    }

    private String formatReplySubject(String subject) {
        if (subject == null) {
            return AppConstants.EMPTY_STRING;
        }
        if (!subject.toLowerCase().startsWith(AppConstants.RE_PREFIX_LOWER)) {
            return AppConstants.RE_PREFIX + subject;
        }
        return subject;
    }

    /**
     * Triggers manual synchronization with Gmail for the user.
     */
    public SyncResultDto syncJobs(String userId) {
        return ingestionService.syncNow(userId);
    }

    /**
     * Sends a thread-preserving reply, manages Gmail labels, and updates application status.
     */
    public JobEmailDto sendReply(String id, SendReplyRequest request, String userId) {
        JobEmail email = findJobEmailOrThrow(id);
        ResumeDocument resume = resolveResumeAttachment(request, userId);

        boolean isInquiry = AppConstants.REPLY_TYPE_INQUIRY.equalsIgnoreCase(request.getReplyType());
        String targetLabel = isInquiry
                ? GmailActionLabel.INQUIRED.getLabelValue()
                : GmailActionLabel.APPLIED.getLabelValue();

        try {
            String sentMessageId = gmailService.sendReply(
                    userId,
                    email,
                    request.getSubject(),
                    request.getBody(),
                    resume,
                    targetLabel,
                    request.isArchiveFromInbox()
            );

            ApplicationStatus newStatus = isInquiry ? ApplicationStatus.INQUIRED : ApplicationStatus.APPLIED;
            email.setApplicationStatus(newStatus);
            email.setRepliedAt(Instant.now());
            email.setReplyMessageId(sentMessageId);
            email.setLastReplyDraft(request.getBody());
            email.setUpdatedAt(Instant.now());

            if (!email.getGmailLabels().contains(targetLabel)) {
                email.getGmailLabels().add(targetLabel);
            }
            if (!email.getGmailLabels().contains(AppConstants.GMAIL_LABEL_IMPORTANT)) {
                email.getGmailLabels().add(AppConstants.GMAIL_LABEL_IMPORTANT);
            }

            JobEmail updated = jobEmailRepository.save(email);
            return toDto(updated);

        } catch (Exception e) {
            log.error("Failed to send reply for job {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, AppConstants.ERR_FAILED_TO_SEND_EMAIL_PREFIX + e.getMessage());
        }
    }

    private ResumeDocument resolveResumeAttachment(SendReplyRequest request, String userId) {
        if (!request.isAttachResume()) {
            return null;
        }
        ResumeDocument resume = resumeService.getActiveResume(userId).orElse(null);
        if (resume == null) {
            log.warn("User requested resume attachment, but no active resume was found. Sending without attachment.");
        }
        return resume;
    }

    /**
     * Dismisses a job opening, marks as DISMISSED, and applies Jobs/Dismissed label in Gmail.
     */
    public JobEmailDto dismissJob(String id, String userId) {
        JobEmail email = findJobEmailOrThrow(id);
        String dismissedLabel = GmailActionLabel.DISMISSED.getLabelValue();

        try {
            if (gmailAuthService.isConfigured(userId)) {
                var gmail = gmailAuthService.getGmailClient(userId);
                gmailService.applyLabelAndArchive(gmail, email.getMessageId(), email.getThreadId(), dismissedLabel, true);
            }
        } catch (Exception e) {
            log.warn("Could not apply dismissed label in Gmail for job {}: {}", id, e.getMessage());
        }

        email.setApplicationStatus(ApplicationStatus.DISMISSED);
        email.setUpdatedAt(Instant.now());
        if (!email.getGmailLabels().contains(dismissedLabel)) {
            email.getGmailLabels().add(dismissedLabel);
        }

        JobEmail updated = jobEmailRepository.save(email);
        return toDto(updated);
    }

    /**
     * Manually updates the employment type / label of a job email and synchronizes Gmail labels.
     */
    public JobEmailDto updateEmploymentType(String id, String employmentTypeStr, String userId) {
        JobEmail email = findJobEmailOrThrow(id);

        if (employmentTypeStr == null || employmentTypeStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.ERR_EMPLOYMENT_TYPE_REQUIRED);
        }

        EmploymentType newType;
        try {
            newType = EmploymentType.valueOf(employmentTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.ERR_INVALID_EMPLOYMENT_TYPE_PREFIX + employmentTypeStr);
        }

        String targetLabel = mapTypeToLabel(newType);

        email.setEmploymentType(newType);
        email.setUpdatedAt(Instant.now());

        try {
            if (gmailAuthService.isConfigured(userId)) {
                var gmail = gmailAuthService.getGmailClient(userId);
                gmailService.applyLabelAndArchive(gmail, email.getMessageId(), email.getThreadId(), targetLabel, false);
                for (GmailEmploymentLabel emp : GmailEmploymentLabel.values()) {
                    email.getGmailLabels().remove(emp.getLabelValue());
                }
                if (!email.getGmailLabels().contains(targetLabel)) {
                    email.getGmailLabels().add(targetLabel);
                }
            }
        } catch (Exception e) {
            log.warn("Could not update Gmail label for job {}: {}", id, e.getMessage());
        }

        JobEmail updated = jobEmailRepository.save(email);
        return toDto(updated);
    }

    private String mapTypeToLabel(EmploymentType type) {
        return switch (type) {
            case C2C -> GmailEmploymentLabel.C2C.getLabelValue();
            case W2 -> GmailEmploymentLabel.W2.getLabelValue();
            case C2H -> GmailEmploymentLabel.C2H.getLabelValue();
            case FULL_TIME -> GmailEmploymentLabel.FULL_TIME.getLabelValue();
            case UNSPECIFIED -> GmailEmploymentLabel.UNSPECIFIED.getLabelValue();
        };
    }

    /**
     * Returns job statistics and metric counts.
     */
    public JobStatsDto getStats() {
        return jobEmailRepository.getAggregatedStats()
                .orElseGet(() -> JobStatsDto.builder().build());
    }

    /**
     * Converts a JobEmail domain entity into JobEmailDto.
     */
    public JobEmailDto toDto(JobEmail e) {
        String body = e.getBodyText();
        if (body != null && !body.isBlank()) {
            body = gmailService.cleanPlainText(body);
        }

        return JobEmailDto.builder()
                .id(e.getId())
                .messageId(e.getMessageId())
                .threadId(e.getThreadId())
                .senderEmail(e.getSenderEmail())
                .senderName(e.getSenderName())
                .recipientEmail(e.getRecipientEmail())
                .subject(e.getSubject())
                .snippet(e.getSnippet())
                .bodyText(body)
                .bodyHtml(e.getBodyHtml())
                .jobTitle(e.getJobTitle())
                .clientOrCompany(e.getClientOrCompany())
                .rate(e.getRate())
                .locationType(e.getLocationType())
                .location(e.getLocation())
                .primarySkills(e.getPrimarySkills())
                .summary(e.getSummary())
                .employmentType(e.getEmploymentType())
                .applicationStatus(e.getApplicationStatus())
                .gmailLabels(e.getGmailLabels())
                .receivedAt(e.getReceivedAt())
                .updatedAt(e.getUpdatedAt())
                .repliedAt(e.getRepliedAt())
                .replyMessageId(e.getReplyMessageId())
                .lastReplyDraft(e.getLastReplyDraft())
                .build();
    }
}
