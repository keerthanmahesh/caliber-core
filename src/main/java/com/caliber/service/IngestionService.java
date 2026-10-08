package com.caliber.service;

import com.caliber.config.GmailConfig;
import com.caliber.dto.AiReplyClassification;
import com.caliber.dto.SyncResultDto;
import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import com.caliber.model.GmailActionLabel;
import com.caliber.model.GmailEmploymentLabel;
import com.caliber.model.JobEmail;
import com.caliber.model.User;
import com.caliber.model.UserSettings;
import com.caliber.repository.JobEmailRepository;
import com.caliber.repository.UserRepository;
import com.caliber.repository.UserSettingsRepository;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.caliber.constant.AppConstants.EMPTY_STRING;
import com.caliber.constant.AppConstants;

/**
 * Service managing scheduled ingestion and on-demand synchronization of job emails from Gmail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final GmailConfig gmailConfig;
    private final GmailService gmailService;
    private final GmailAuthService gmailAuthService;
    private final ClassificationEngine classificationEngine;
    private final AiClientService aiClientService;
    private final JobEmailRepository jobEmailRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    private static final Pattern SENDER_EXTRACTOR = Pattern.compile("^(?:\"?([^\"]*)\"?\\s*)?<([^>]+)>$");

    /**
     * Scheduled background polling (configurable via caliber.gmail.poll-interval-hours).
     * Evaluates each user's poll interval in hours against their lastSyncedAt timestamp before syncing.
     */
    @Scheduled(fixedDelayString = "PT${caliber.gmail.poll-interval-hours:1}H", initialDelay = 60000)
    public void scheduledPoll() {
        log.debug("Running scheduled Gmail ingestion poll check for connected users...");
        List<UserSettings> connectedUsers = userSettingsRepository.findByGmailConnectedTrue();
        if (connectedUsers.isEmpty()) {
            log.debug("No active users with Gmail connected. Scheduled poll skipped.");
            return;
        }

        Instant now = Instant.now();
        for (UserSettings settings : connectedUsers) {
            String userId = settings.getUserId();
            if (userId == null || userId.isBlank()) {
                continue;
            }

            int defaultIntervalHours = gmailConfig.getPollIntervalHours() > 0
                    ? gmailConfig.getPollIntervalHours()
                    : 1;

            int intervalHours = settings.getEffectivePollIntervalHours() > 0
                    ? settings.getEffectivePollIntervalHours()
                    : defaultIntervalHours;

            if (settings.getLastSyncedAt() != null) {
                long minutesSinceLastSync = Duration.between(settings.getLastSyncedAt(), now).toMinutes();
                long requiredMinutes = intervalHours * 60L;
                if (minutesSinceLastSync < requiredMinutes) {
                    log.debug("Skipping user {}: last synced {} min(s) ago (interval: {} hr(s)).",
                            userId, minutesSinceLastSync, intervalHours);
                    continue;
                }
            }

            try {
                if (gmailAuthService.isConfigured(userId)) {
                    SyncResultDto result = syncNow(userId);
                    log.info("Scheduled sync complete for user {}: {}", userId, result.getMessage());
                } else {
                    log.debug("Gmail not configured yet for user {}. Scheduled poll skipped.", userId);
                }
            } catch (Exception e) {
                log.error("Scheduled sync encountered an error for user {}: {}", userId, e.getMessage());
            }
        }
    }

    /**
     * Synchronizes Gmail inbox messages immediately for the specified user.
     */
    public SyncResultDto syncNow(String userId) {
        log.info("Starting manual/triggered Gmail synchronization for user '{}'", userId);

        if (!gmailAuthService.isConfigured(userId)) {
            return buildNotConfiguredResult("Gmail API is not configured. Please supply Client ID, Client Secret, and Refresh Token in settings or .env.");
        }

        String query = resolveQuery(userId);
        if (query == null || query.isBlank()) {
            return buildNotConfiguredResult("Gmail search query is not configured. Please enter your search query in Settings.");
        }

        return executeSync(userId, query);
    }

    private SyncResultDto executeSync(String userId, String query) {
        SyncCounters counters = new SyncCounters();
        try {
            long maxResults = gmailConfig.getMaxResults();
            List<Message> messages = gmailService.listMessages(userId, query, maxResults);
            counters.scanned = messages.size();
            log.info("Found {} messages matching query '{}'", counters.scanned, query);

            Gmail gmail = gmailAuthService.getGmailClient(userId);
            String userEmail = resolveUserEmail(userId, gmail);

            for (Message msgStub : messages) {
                processMessageStub(msgStub, userId, userEmail, gmail, counters);
            }

            updateLastSyncedAt(userId);
            return buildSuccessResult(counters);

        } catch (Exception e) {
            log.error("Sync error: {}", e.getMessage(), e);
            return buildFailureResult(counters, e.getMessage());
        }
    }

    private void updateLastSyncedAt(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            userSettingsRepository.findByUserId(userId).ifPresent(settings -> {
                settings.setLastSyncedAt(Instant.now());
                userSettingsRepository.save(settings);
            });
        } catch (Exception e) {
            log.warn("Failed to update lastSyncedAt for user {}: {}", userId, e.getMessage());
        }
    }

    private void processMessageStub(Message msgStub, String userId, String userEmail, Gmail gmail, SyncCounters counters) {
        String msgId = msgStub.getId();
        try {
            if (isAlreadyIngested(userId, msgId)) {
                counters.duplicatesSkipped++;
                return;
            }

            Message fullMsg = gmailService.getMessage(userId, msgId);
            if (hasExcludedLabels(fullMsg.getLabelIds())) {
                log.debug("Skipping message {} with non-inbound labels: {}", msgId, fullMsg.getLabelIds());
                counters.duplicatesSkipped++;
                return;
            }

            JobEmail email = parseMessage(fullMsg, userId);
            GmailEmploymentLabel preExistingEmpLabel = applyPreExistingLabels(email, fullMsg.getLabelIds(), gmail);

            if (isSelfSent(email, userEmail)) {
                log.debug("Skipping message {} sent by authenticated user {}", msgId, userEmail);
                counters.duplicatesSkipped++;
                return;
            }

            if (handleExistingThread(email, userId, gmail)) {
                counters.threadsUpdated++;
                return;
            }

            if (handleDuplicateBlast(email, userId)) {
                counters.duplicatesSkipped++;
                return;
            }

            ingestNewJobEmail(email, userId, gmail, preExistingEmpLabel, counters);

        } catch (Exception ex) {
            log.error("Failed to process message {}: {}", msgId, ex.getMessage());
        }
    }

    private boolean isAlreadyIngested(String userId, String msgId) {
        if (jobEmailRepository.existsByUserIdAndMessageId(userId, msgId) || jobEmailRepository.existsByMessageId(msgId)) {
            log.debug("Message {} already ingested. Skipping duplicate.", msgId);
            return true;
        }
        return false;
    }

    private boolean hasExcludedLabels(List<String> labelIds) {
        if (labelIds == null) return false;
        return labelIds.contains("SENT") || labelIds.contains("DRAFT")
                || labelIds.contains("TRASH") || labelIds.contains("SPAM");
    }

    private boolean isSelfSent(JobEmail email, String userEmail) {
        return userEmail != null && email.getSenderEmail() != null
                && userEmail.equalsIgnoreCase(email.getSenderEmail().trim());
    }

    private GmailEmploymentLabel applyPreExistingLabels(JobEmail email, List<String> labelIds, Gmail gmail) {
        GmailEmploymentLabel preExistingEmpLabel = gmailService.getExistingEmploymentLabel(gmail, labelIds);
        if (preExistingEmpLabel != null) {
            email.setEmploymentType(preExistingEmpLabel.toEmploymentType());
            if (!email.getGmailLabels().contains(preExistingEmpLabel.getLabelValue())) {
                email.getGmailLabels().add(preExistingEmpLabel.getLabelValue());
            }
            log.info("Message {} already has Gmail employment label '{}'. Skipping LLM classification.",
                    email.getMessageId(), preExistingEmpLabel.getLabelValue());
        }

        GmailActionLabel preExistingActionLabel = gmailService.getExistingActionLabel(gmail, labelIds);
        if (preExistingActionLabel != null) {
            ApplicationStatus actionStatus = preExistingActionLabel.toApplicationStatus();
            if (actionStatus != null) {
                email.setApplicationStatus(actionStatus);
            }
            if (!email.getGmailLabels().contains(preExistingActionLabel.getLabelValue())) {
                email.getGmailLabels().add(preExistingActionLabel.getLabelValue());
            }
            log.info("Message {} already has Gmail action label '{}'.", email.getMessageId(), preExistingActionLabel.getLabelValue());
        }
        return preExistingEmpLabel;
    }

    private boolean handleExistingThread(JobEmail email, String userId, Gmail gmail) {
        if (email.getThreadId() == null || email.getThreadId().isBlank()) {
            return false;
        }
        Optional<JobEmail> threadMatch = jobEmailRepository.findByUserIdAndThreadId(userId, email.getThreadId())
                .or(() -> jobEmailRepository.findByThreadId(email.getThreadId()));

        if (threadMatch.isEmpty()) {
            return false;
        }

        JobEmail existing = threadMatch.get();
        log.info("Message {} belongs to existing thread {}. Updating thread record.", email.getMessageId(), email.getThreadId());
        updateThreadMetadata(existing, email);

        boolean isInquired = existing.getApplicationStatus() == ApplicationStatus.INQUIRED;
        boolean isUnspecified = existing.getEmploymentType() == EmploymentType.UNSPECIFIED || existing.getEmploymentType() == null;

        if (isInquired || isUnspecified) {
            classifyAndUpdateThreadReply(existing, email, userId, gmail);
        }

        jobEmailRepository.save(existing);
        return true;
    }

    private void updateThreadMetadata(JobEmail existing, JobEmail email) {
        existing.setMessageId(email.getMessageId());
        existing.setUpdatedAt(Instant.now());
        if (email.getSnippet() != null && !email.getSnippet().isBlank()) {
            existing.setSnippet(email.getSnippet());
        }
        if (email.getBodyText() != null && !email.getBodyText().isBlank()) {
            existing.setBodyText(email.getBodyText());
        }
        if (email.getBodyHtml() != null && !email.getBodyHtml().isBlank()) {
            existing.setBodyHtml(email.getBodyHtml());
        }
        if (email.getReceivedAt() != null && (existing.getReceivedAt() == null || email.getReceivedAt().isAfter(existing.getReceivedAt()))) {
            existing.setReceivedAt(email.getReceivedAt());
        }
    }

    private void classifyAndUpdateThreadReply(JobEmail existing, JobEmail email, String userId, Gmail gmail) {
        String replyText = resolveReplyContent(email);

        log.info("Recruiter replied on thread {} (appStatus={}, empType={}). Invoking AI to determine new contract label...",
                existing.getThreadId(), existing.getApplicationStatus(), existing.getEmploymentType());

        AiReplyClassification classification = aiClientService.classifyRecruiterReply(
                existing.getJobTitle(),
                existing.getLastReplyDraft(),
                replyText,
                userId
        );

        EmploymentType newEmpType = classification.getEmploymentType() != null
                ? classification.getEmploymentType()
                : EmploymentType.UNSPECIFIED;

        existing.setEmploymentType(newEmpType);

        if (classification.getRate() != null && !classification.getRate().isBlank()
                && ("Not Mentioned".equalsIgnoreCase(existing.getRate()) || existing.getRate() == null)) {
            existing.setRate(classification.getRate());
        }

        log.info("AI determined new label for thread {}: {} ({})",
                existing.getThreadId(), newEmpType, classification.getReasoning());

        String updatedTypeLabel = resolveTypeLabel(newEmpType);
        if (updatedTypeLabel != null) {
            applyGmailLabelSafely(gmail, existing, updatedTypeLabel);
        }
    }

    private String resolveReplyContent(JobEmail email) {
        if (email.getBodyText() != null && !email.getBodyText().isBlank()) {
            return email.getBodyText();
        }
        if (email.getSnippet() != null && !email.getSnippet().isBlank()) {
            return email.getSnippet();
        }
        return EMPTY_STRING;
    }

    private boolean handleDuplicateBlast(JobEmail email, String userId) {
        Optional<JobEmail> blastMatch = findDuplicateBlast(userId, email);
        if (blastMatch.isEmpty()) {
            return false;
        }

        JobEmail existing = blastMatch.get();
        log.info("Message {} is a duplicate recruiter blast from sender {} with subject '{}'. Merging into existing record.",
                email.getMessageId(), email.getSenderEmail(), email.getSubject());

        existing.setUpdatedAt(Instant.now());
        if (email.getReceivedAt() != null && (existing.getReceivedAt() == null || email.getReceivedAt().isAfter(existing.getReceivedAt()))) {
            existing.setReceivedAt(email.getReceivedAt());
            existing.setMessageId(email.getMessageId());
            if (email.getSnippet() != null && !email.getSnippet().isBlank()) {
                existing.setSnippet(email.getSnippet());
            }
        }
        jobEmailRepository.save(existing);
        return true;
    }

    private void ingestNewJobEmail(JobEmail email, String userId, Gmail gmail,
                                   GmailEmploymentLabel preExistingEmpLabel, SyncCounters counters) {
        classificationEngine.classifyAndEnrich(email, userId);

        String typeLabel = resolveTypeLabel(email.getEmploymentType());
        if (typeLabel != null) {
            try {
                if (preExistingEmpLabel == null || !preExistingEmpLabel.getLabelValue().equalsIgnoreCase(typeLabel)) {
                    gmailService.applyLabelAndArchive(gmail, email.getMessageId(), email.getThreadId(), typeLabel, false);
                }
                for (GmailEmploymentLabel emp : GmailEmploymentLabel.values()) {
                    email.getGmailLabels().remove(emp.getLabelValue());
                }
                if (!email.getGmailLabels().contains(typeLabel)) {
                    email.getGmailLabels().add(typeLabel);
                }
            } catch (Exception e) {
                log.warn("Could not apply label {} to message {}: {}", typeLabel, email.getMessageId(), e.getMessage());
            }
        }

        try {
            jobEmailRepository.save(email);
            counters.ingested++;
        } catch (DuplicateKeyException dke) {
            log.warn("Duplicate key detected for messageId {}: {}", email.getMessageId(), dke.getMessage());
            counters.duplicatesSkipped++;
            return;
        }

        EmploymentType empType = email.getEmploymentType() != null ? email.getEmploymentType() : EmploymentType.UNSPECIFIED;
        switch (empType) {
            case C2C -> counters.c2c++;
            case C2H -> counters.c2h++;
            case W2 -> counters.w2++;
            case FULL_TIME -> counters.fullTime++;
            case UNSPECIFIED -> counters.unspecified++;
        }
    }

    private String resolveTypeLabel(EmploymentType empType) {
        if (empType == null) empType = EmploymentType.UNSPECIFIED;
        return switch (empType) {
            case C2C -> GmailEmploymentLabel.C2C.getLabelValue();
            case C2H -> GmailEmploymentLabel.C2H.getLabelValue();
            case W2 -> GmailEmploymentLabel.W2.getLabelValue();
            case FULL_TIME -> GmailEmploymentLabel.FULL_TIME.getLabelValue();
            case UNSPECIFIED -> GmailEmploymentLabel.UNSPECIFIED.getLabelValue();
        };
    }

    private void applyGmailLabelSafely(Gmail gmail, JobEmail email, String label) {
        try {
            gmailService.applyLabelAndArchive(gmail, email.getMessageId(), email.getThreadId(), label, false);
            for (GmailEmploymentLabel emp : GmailEmploymentLabel.values()) {
                email.getGmailLabels().remove(emp.getLabelValue());
            }
            if (!email.getGmailLabels().contains(label)) {
                email.getGmailLabels().add(label);
            }
        } catch (Exception e) {
            log.warn("Could not update Gmail label for message/thread {}: {}", email.getMessageId(), e.getMessage());
        }
    }

    private SyncResultDto buildNotConfiguredResult(String message) {
        return SyncResultDto.builder()
                .success(false)
                .syncedAt(Instant.now())
                .message(message)
                .build();
    }

    private SyncResultDto buildSuccessResult(SyncCounters c) {
        String summary = String.format("Sync successful: Scanned %d messages, ingested %d new (%d C2C, %d C2H, %d W2, %d Full-Time, %d Unspecified), %d threads updated, %d duplicates skipped.",
                c.scanned, c.ingested, c.c2c, c.c2h, c.w2, c.fullTime, c.unspecified, c.threadsUpdated, c.duplicatesSkipped);

        return SyncResultDto.builder()
                .success(true)
                .messagesScanned(c.scanned)
                .newEmailsIngested(c.ingested)
                .c2cCount(c.c2c)
                .c2hCount(c.c2h)
                .w2Count(c.w2)
                .fullTimeCount(c.fullTime)
                .unspecifiedCount(c.unspecified)
                .duplicatesSkipped(c.duplicatesSkipped)
                .threadsUpdated(c.threadsUpdated)
                .syncedAt(Instant.now())
                .message(summary)
                .build();
    }

    private SyncResultDto buildFailureResult(SyncCounters c, String errorMessage) {
        return SyncResultDto.builder()
                .success(false)
                .messagesScanned(c.scanned)
                .newEmailsIngested(c.ingested)
                .duplicatesSkipped(c.duplicatesSkipped)
                .threadsUpdated(c.threadsUpdated)
                .syncedAt(Instant.now())
                .message("Sync failed: " + errorMessage)
                .build();
    }

    private static class SyncCounters {
        int scanned;
        int ingested;
        int c2c;
        int c2h;
        int w2;
        int fullTime;
        int unspecified;
        int duplicatesSkipped;
        int threadsUpdated;
    }

    private JobEmail parseMessage(Message msg, String userId) {
        List<MessagePartHeader> headers = msg.getPayload() != null ? msg.getPayload().getHeaders() : null;

        String subject = gmailService.getHeaderValue(headers, "Subject");
        String from = gmailService.getHeaderValue(headers, "From");
        String to = gmailService.getHeaderValue(headers, "To");
        String senderEmail = from;
        String senderName = from;

        if (from != null) {
            Matcher matcher = SENDER_EXTRACTOR.matcher(from.trim());
            if (matcher.find()) {
                String namePart = matcher.group(1);
                String emailPart = matcher.group(2);
                if (emailPart != null) senderEmail = emailPart.trim();
                if (namePart != null && !namePart.isBlank()) senderName = namePart.trim();
                else senderName = senderEmail;
            }
        }

        String plainText = gmailService.extractPlainText(msg.getPayload());
        String html = gmailService.extractHtml(msg.getPayload());

        Instant receivedAt = Instant.now();
        if (msg.getInternalDate() != null) {
            receivedAt = Instant.ofEpochMilli(msg.getInternalDate());
        }

        return JobEmail.builder()
                .messageId(msg.getId())
                .threadId(msg.getThreadId())
                .senderEmail(senderEmail)
                .senderName(senderName)
                .recipientEmail(to)
                .subject(subject != null ? subject : "No Subject")
                .snippet(msg.getSnippet())
                .bodyText(plainText)
                .bodyHtml(html)
                .receivedAt(receivedAt)
                .updatedAt(Instant.now())
                .userId(userId)
                .build();
    }

    private String resolveQuery(String userId) {
        if (userId != null) {
            Optional<UserSettings> s = userSettingsRepository.findByUserId(userId);
            if (s.isPresent() && s.get().getGmailSearchQuery() != null && !s.get().getGmailSearchQuery().isBlank()) {
                return s.get().getGmailSearchQuery().trim();
            }
        }
        return null;
    }

    private String resolveUserEmail(String userId, Gmail gmail) {
        try {
            if (userId != null && !userId.isBlank()) {
                Optional<User> userOpt = userRepository.findById(userId)
                        .or(() -> userRepository.findBySub(userId));
                if (userOpt.isPresent() && userOpt.get().getEmail() != null && !userOpt.get().getEmail().isBlank()) {
                    return userOpt.get().getEmail();
                }
            }
            if (gmail != null) {
                return gmail.users().getProfile(AppConstants.GMAIL_USER_ME).execute().getEmailAddress();
            }
        } catch (Exception e) {
            log.debug("Could not resolve authenticated user email: {}", e.getMessage());
        }
        return null;
    }

    private Optional<JobEmail> findDuplicateBlast(String userId, JobEmail email) {
        if (email.getSenderEmail() == null || email.getSubject() == null) {
            return Optional.empty();
        }

        String sender = email.getSenderEmail().trim();
        String rawSubject = email.getSubject().trim();
        String normSubject = normalizeSubject(rawSubject);

        Optional<JobEmail> match = jobEmailRepository.findFirstByUserIdAndSenderEmailAndSubjectIgnoreCase(userId, sender, rawSubject)
                .or(() -> jobEmailRepository.findFirstBySenderEmailAndSubjectIgnoreCase(sender, rawSubject));

        if (match.isEmpty() && !normSubject.equalsIgnoreCase(rawSubject) && !normSubject.isBlank()) {
            match = jobEmailRepository.findFirstByUserIdAndSenderEmailAndSubjectIgnoreCase(userId, sender, normSubject)
                    .or(() -> jobEmailRepository.findFirstBySenderEmailAndSubjectIgnoreCase(sender, normSubject));
        }

        if (match.isPresent()) {
            JobEmail existing = match.get();
            if (existing.getReceivedAt() != null && email.getReceivedAt() != null) {
                long hoursBetween = Math.abs(Duration.between(existing.getReceivedAt(), email.getReceivedAt()).toHours());
                if (hoursBetween <= 120) { // 5 days blast window
                    return match;
                }
            } else {
                return match;
            }
        }

        return Optional.empty();
    }

    private String normalizeSubject(String subject) {
        if (subject == null) return "";
        return subject.replaceAll("^(?i)(re|fwd|fw):\\s*", "").trim();
    }
}
