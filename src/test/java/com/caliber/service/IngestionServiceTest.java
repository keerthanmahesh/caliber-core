package com.caliber.service;

import com.caliber.config.GmailConfig;
import com.caliber.dto.AiReplyClassification;
import com.caliber.dto.SyncResultDto;
import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import com.caliber.model.JobEmail;
import com.caliber.model.User;
import com.caliber.model.UserSettings;
import com.caliber.repository.JobEmailRepository;
import com.caliber.repository.UserRepository;
import com.caliber.repository.UserSettingsRepository;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private GmailConfig gmailConfig;

    @Mock
    private GmailService gmailService;

    @Mock
    private GmailAuthService gmailAuthService;

    @Mock
    private ClassificationEngine classificationEngine;

    @Mock
    private AiClientService aiClientService;

    @Mock
    private JobEmailRepository jobEmailRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Gmail gmail;

    private IngestionService ingestionService;

    private static final String USER_ID = "user-123";
    private static final String USER_EMAIL = "candidate@example.com";

    @BeforeEach
    void setUp() throws Exception {
        ingestionService = new IngestionService(
                gmailConfig,
                gmailService,
                gmailAuthService,
                classificationEngine,
                aiClientService,
                jobEmailRepository,
                userSettingsRepository,
                userRepository
        );

        lenient().when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                UserSettings.builder().userId(USER_ID).gmailSearchQuery("label:inbox").build()
        ));
        lenient().when(gmailConfig.getMaxResults()).thenReturn(50L);
        lenient().when(gmailConfig.getPollIntervalMs()).thenReturn(60000L);
        lenient().when(gmailAuthService.isConfigured(USER_ID)).thenReturn(true);
        lenient().when(gmailAuthService.getGmailClient(USER_ID)).thenReturn(gmail);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(
                User.builder().id(USER_ID).email(USER_EMAIL).build()
        ));

        lenient().when(gmailService.getHeaderValue(any(), anyString())).thenAnswer(invocation -> {
            List<MessagePartHeader> headers = invocation.getArgument(0);
            String name = invocation.getArgument(1);
            if (headers == null || name == null) return null;
            for (MessagePartHeader h : headers) {
                if (name.equalsIgnoreCase(h.getName())) {
                    return h.getValue();
                }
            }
            return null;
        });

        lenient().when(gmailService.extractPlainText(any())).thenReturn("Email body text");
        lenient().when(gmailService.extractHtml(any())).thenReturn("<p>Email body html</p>");
    }

    @Test
    void testMessageIdDuplicateSkipped() throws Exception {
        Message stub = new Message().setId("msg-101").setThreadId("thread-101");
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(stub));

        when(jobEmailRepository.existsByUserIdAndMessageId(USER_ID, "msg-101")).thenReturn(true);

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getMessagesScanned());
        assertEquals(0, result.getNewEmailsIngested());
        assertEquals(1, result.getDuplicatesSkipped());
        verify(gmailService, never()).getMessage(anyString(), anyString());
        verify(jobEmailRepository, never()).save(any());
    }

    @Test
    void testSentOrDraftLabelSkipped() throws Exception {
        Message stub = new Message().setId("msg-102").setThreadId("thread-102");
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(stub));

        when(jobEmailRepository.existsByUserIdAndMessageId(USER_ID, "msg-102")).thenReturn(false);
        when(jobEmailRepository.existsByMessageId("msg-102")).thenReturn(false);

        Message fullMsg = new Message()
                .setId("msg-102")
                .setThreadId("thread-102")
                .setLabelIds(List.of("SENT", "INBOX"));

        when(gmailService.getMessage(USER_ID, "msg-102")).thenReturn(fullMsg);

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getNewEmailsIngested());
        assertEquals(1, result.getDuplicatesSkipped());
        verify(jobEmailRepository, never()).save(any());
    }

    @Test
    void testSelfSentMessageSkipped() throws Exception {
        Message stub = new Message().setId("msg-103").setThreadId("thread-103");
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(stub));

        when(jobEmailRepository.existsByUserIdAndMessageId(USER_ID, "msg-103")).thenReturn(false);
        when(jobEmailRepository.existsByMessageId("msg-103")).thenReturn(false);

        MessagePartHeader headerFrom = new MessagePartHeader().setName("From").setValue("Candidate <candidate@example.com>");
        MessagePartHeader headerSubject = new MessagePartHeader().setName("Subject").setValue("Re: My Resume Submission");
        MessagePart payload = new MessagePart().setHeaders(List.of(headerFrom, headerSubject));
        Message fullMsg = new Message()
                .setId("msg-103")
                .setThreadId("thread-103")
                .setPayload(payload)
                .setLabelIds(List.of("INBOX"));

        when(gmailService.getMessage(USER_ID, "msg-103")).thenReturn(fullMsg);

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getNewEmailsIngested());
        assertEquals(1, result.getDuplicatesSkipped());
        verify(jobEmailRepository, never()).save(any());
    }

    @Test
    void testThreadDuplicateMergesIntoExistingRecord() throws Exception {
        Message stub = new Message().setId("msg-followup").setThreadId("thread-abc");
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(stub));

        when(jobEmailRepository.existsByUserIdAndMessageId(USER_ID, "msg-followup")).thenReturn(false);
        when(jobEmailRepository.existsByMessageId("msg-followup")).thenReturn(false);

        MessagePartHeader headerFrom = new MessagePartHeader().setName("From").setValue("recruiter@staffing.com");
        MessagePartHeader headerSubject = new MessagePartHeader().setName("Subject").setValue("Re: Lead Java Developer");
        MessagePart payload = new MessagePart().setHeaders(List.of(headerFrom, headerSubject));
        Message fullMsg = new Message()
                .setId("msg-followup")
                .setThreadId("thread-abc")
                .setSnippet("Following up on the C2C role...")
                .setPayload(payload)
                .setLabelIds(List.of("INBOX"));

        when(gmailService.getMessage(USER_ID, "msg-followup")).thenReturn(fullMsg);

        JobEmail existingThread = JobEmail.builder()
                .id("db-record-1")
                .messageId("msg-original")
                .threadId("thread-abc")
                .senderEmail("recruiter@staffing.com")
                .subject("Lead Java Developer")
                .applicationStatus(ApplicationStatus.APPLIED) // User already applied!
                .employmentType(EmploymentType.C2C)
                .receivedAt(Instant.now().minusSeconds(3600))
                .build();

        when(jobEmailRepository.findByUserIdAndThreadId(USER_ID, "thread-abc"))
                .thenReturn(Optional.of(existingThread));

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getNewEmailsIngested());
        assertEquals(1, result.getThreadsUpdated());
        // Verify existing was updated with latest messageId and snippet, but kept status APPLIED
        assertEquals("msg-followup", existingThread.getMessageId());
        assertEquals("Following up on the C2C role...", existingThread.getSnippet());
        assertEquals(ApplicationStatus.APPLIED, existingThread.getApplicationStatus());
        verify(jobEmailRepository).save(existingThread);
    }

    @Test
    void testRecruiterBlastDuplicateMerged() throws Exception {
        Message stub = new Message().setId("msg-blast-2").setThreadId("thread-diff-999");
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(stub));

        when(jobEmailRepository.existsByUserIdAndMessageId(USER_ID, "msg-blast-2")).thenReturn(false);
        when(jobEmailRepository.existsByMessageId("msg-blast-2")).thenReturn(false);
        when(jobEmailRepository.findByUserIdAndThreadId(USER_ID, "thread-diff-999")).thenReturn(Optional.empty());
        when(jobEmailRepository.findByThreadId("thread-diff-999")).thenReturn(Optional.empty());

        MessagePartHeader headerFrom = new MessagePartHeader().setName("From").setValue("recruiter@agency.com");
        MessagePartHeader headerSubject = new MessagePartHeader().setName("Subject").setValue("Senior Java Architect");
        MessagePart payload = new MessagePart().setHeaders(List.of(headerFrom, headerSubject));
        Message fullMsg = new Message()
                .setId("msg-blast-2")
                .setThreadId("thread-diff-999")
                .setSnippet("Immediate need for Senior Java Architect")
                .setPayload(payload)
                .setLabelIds(List.of("INBOX"));

        when(gmailService.getMessage(USER_ID, "msg-blast-2")).thenReturn(fullMsg);

        JobEmail existingBlast = JobEmail.builder()
                .id("db-blast-1")
                .messageId("msg-blast-1")
                .threadId("thread-diff-111")
                .senderEmail("recruiter@agency.com")
                .subject("Senior Java Architect")
                .applicationStatus(ApplicationStatus.PENDING)
                .employmentType(EmploymentType.C2C)
                .receivedAt(Instant.now().minusSeconds(1800))
                .build();

        when(jobEmailRepository.findFirstByUserIdAndSenderEmailAndSubjectIgnoreCase(
                eq(USER_ID), eq("recruiter@agency.com"), eq("Senior Java Architect")
        )).thenReturn(Optional.of(existingBlast));

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getNewEmailsIngested());
        assertEquals(1, result.getDuplicatesSkipped());
        assertEquals("msg-blast-2", existingBlast.getMessageId());
        verify(jobEmailRepository).save(existingBlast);
    }

    @Test
    void testNewUniqueEmailIngested() throws Exception {
        Message stub = new Message().setId("msg-unique").setThreadId("thread-unique");
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(stub));

        when(jobEmailRepository.existsByUserIdAndMessageId(USER_ID, "msg-unique")).thenReturn(false);
        when(jobEmailRepository.existsByMessageId("msg-unique")).thenReturn(false);
        when(jobEmailRepository.findByUserIdAndThreadId(USER_ID, "thread-unique")).thenReturn(Optional.empty());
        when(jobEmailRepository.findByThreadId("thread-unique")).thenReturn(Optional.empty());

        MessagePartHeader headerFrom = new MessagePartHeader().setName("From").setValue("recruiter@techjobs.com");
        MessagePartHeader headerSubject = new MessagePartHeader().setName("Subject").setValue("Full Stack React Engineer");
        MessagePart payload = new MessagePart().setHeaders(List.of(headerFrom, headerSubject));
        Message fullMsg = new Message()
                .setId("msg-unique")
                .setThreadId("thread-unique")
                .setSnippet("Looking for React Engineer on C2C basis")
                .setPayload(payload)
                .setLabelIds(List.of("INBOX"));

        when(gmailService.getMessage(USER_ID, "msg-unique")).thenReturn(fullMsg);

        doAnswer(inv -> {
            JobEmail e = inv.getArgument(0);
            e.setEmploymentType(EmploymentType.C2C);
            return null;
        }).when(classificationEngine).classifyAndEnrich(any(JobEmail.class), eq(USER_ID));

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getMessagesScanned());
        assertEquals(1, result.getNewEmailsIngested());
        assertEquals(1, result.getC2cCount());
        verify(jobEmailRepository).save(any(JobEmail.class));
    }

    @Test
    void testSyncFailsWhenSearchQueryNotConfigured() throws Exception {
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertFalse(result.isSuccess());
        assertEquals("Gmail search query is not configured. Please enter your search query in Settings.", result.getMessage());
        verify(gmailService, never()).listMessages(any(), any(), anyLong());
    }

    @Test
    void testRecruiterReplyOnInquiredThreadInvokesAiClassification() throws Exception {
        when(gmailAuthService.isConfigured(USER_ID)).thenReturn(true);
        when(gmailAuthService.getGmailClient(USER_ID)).thenReturn(gmail);
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(50L)))
                .thenReturn(List.of(new Message().setId("reply-msg-1").setThreadId("thread-123")));

        MessagePartHeader fromHeader = new MessagePartHeader().setName("From").setValue("recruiter@example.com");
        MessagePartHeader subHeader = new MessagePartHeader().setName("Subject").setValue("Re: Java Architect");
        MessagePart payload = new MessagePart()
                .setHeaders(List.of(fromHeader, subHeader))
                .setBody(new MessagePartBody().setData(Base64.getUrlEncoder().encodeToString("Yes, we can do C2C at $85/hr".getBytes())));

        Message fullMsg = new Message()
                .setId("reply-msg-1")
                .setThreadId("thread-123")
                .setPayload(payload)
                .setLabelIds(List.of("INBOX"));

        when(gmailService.getMessage(USER_ID, "reply-msg-1")).thenReturn(fullMsg);

        JobEmail existingInquired = JobEmail.builder()
                .id("job-1")
                .userId(USER_ID)
                .threadId("thread-123")
                .messageId("msg-initial")
                .applicationStatus(ApplicationStatus.INQUIRED)
                .employmentType(EmploymentType.UNSPECIFIED)
                .lastReplyDraft("Is this role open for C2C?")
                .build();

        when(jobEmailRepository.findByUserIdAndThreadId(USER_ID, "thread-123"))
                .thenReturn(Optional.of(existingInquired));

        when(aiClientService.classifyRecruiterReply(any(), any(), any(), eq(USER_ID)))
                .thenReturn(AiReplyClassification.builder()
                        .employmentType(EmploymentType.C2C)
                        .rate("$85/hr")
                        .reasoning("Recruiter explicitly confirmed C2C")
                        .build());

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getThreadsUpdated());
        assertEquals(EmploymentType.C2C, existingInquired.getEmploymentType());
        assertEquals("$85/hr", existingInquired.getRate());
        verify(jobEmailRepository).save(existingInquired);
        verify(gmailService).applyLabelAndArchive(eq(gmail), any(), eq("thread-123"), eq("Jobs/C2C"), eq(false));
    }

    @Test
    void testSyncUsesConfiguredMaxResults() throws Exception {
        when(gmailConfig.getMaxResults()).thenReturn(25L);
        when(gmailService.listMessages(eq(USER_ID), anyString(), eq(25L)))
                .thenReturn(List.of());

        SyncResultDto result = ingestionService.syncNow(USER_ID);

        assertTrue(result.isSuccess());
        verify(gmailService).listMessages(eq(USER_ID), anyString(), eq(25L));
    }

    @Test
    void testScheduledPollWhenNoConnectedUsers() {
        when(userSettingsRepository.findByGmailConnectedTrue()).thenReturn(List.of());

        ingestionService.scheduledPoll();

        verify(gmailAuthService, never()).isConfigured(anyString());
    }

    @Test
    void testScheduledPollWithConnectedUsers() throws Exception {
        UserSettings user1 = UserSettings.builder().userId(USER_ID).gmailSearchQuery("query1").gmailConnected(true).build();
        UserSettings user2 = UserSettings.builder().userId("user-456").gmailSearchQuery("query2").gmailConnected(true).build();
        when(userSettingsRepository.findByGmailConnectedTrue()).thenReturn(List.of(user1, user2));
        when(userSettingsRepository.findByUserId("user-456")).thenReturn(Optional.of(user2));
        when(gmailAuthService.isConfigured("user-456")).thenReturn(true);
        when(gmailAuthService.getGmailClient("user-456")).thenReturn(gmail);
        when(gmailService.listMessages(eq(USER_ID), anyString(), anyLong())).thenReturn(List.of());
        when(gmailService.listMessages(eq("user-456"), anyString(), anyLong())).thenReturn(List.of());

        ingestionService.scheduledPoll();

        verify(gmailService).listMessages(eq(USER_ID), anyString(), anyLong());
        verify(gmailService).listMessages(eq("user-456"), anyString(), anyLong());
    }

    @Test
    void testScheduledPollContinuesWhenOneUserFails() throws Exception {
        UserSettings user1 = UserSettings.builder().userId("failing-user").gmailSearchQuery("q").gmailConnected(true).build();
        UserSettings user2 = UserSettings.builder().userId(USER_ID).gmailSearchQuery("query1").gmailConnected(true).build();
        when(userSettingsRepository.findByGmailConnectedTrue()).thenReturn(List.of(user1, user2));
        when(gmailAuthService.isConfigured("failing-user")).thenThrow(new RuntimeException("OAuth token revoked"));
        when(gmailService.listMessages(eq(USER_ID), anyString(), anyLong())).thenReturn(List.of());

        ingestionService.scheduledPoll();

        verify(gmailService).listMessages(eq(USER_ID), anyString(), anyLong());
    }

    @Test
    void testScheduledPollSkipsUserIfIntervalNotReached() throws Exception {
        UserSettings userRecent = UserSettings.builder()
                .userId(USER_ID)
                .gmailSearchQuery("query1")
                .gmailConnected(true)
                .pollIntervalMinutes(15)
                .lastSyncedAt(Instant.now().minus(java.time.Duration.ofMinutes(5)))
                .build();

        when(userSettingsRepository.findByGmailConnectedTrue()).thenReturn(List.of(userRecent));

        ingestionService.scheduledPoll();

        verify(gmailService, never()).listMessages(anyString(), anyString(), anyLong());
    }

    @Test
    void testScheduledPollSyncsUserIfIntervalReached() throws Exception {
        UserSettings userDue = UserSettings.builder()
                .userId(USER_ID)
                .gmailSearchQuery("query1")
                .gmailConnected(true)
                .pollIntervalMinutes(10)
                .lastSyncedAt(Instant.now().minus(java.time.Duration.ofMinutes(12)))
                .build();

        when(userSettingsRepository.findByGmailConnectedTrue()).thenReturn(List.of(userDue));
        when(gmailService.listMessages(eq(USER_ID), anyString(), anyLong())).thenReturn(List.of());

        ingestionService.scheduledPoll();

        verify(gmailService).listMessages(eq(USER_ID), anyString(), anyLong());
        verify(userSettingsRepository, atLeastOnce()).save(any(UserSettings.class));
    }

    @Test
    void testScheduledPollFallsBackToConfiguredPollIntervalWhenUserSettingNull() throws Exception {
        // Set server poll interval to 10 minutes (600000 ms)
        when(gmailConfig.getPollIntervalMs()).thenReturn(600000L);

        UserSettings userNullInterval = UserSettings.builder()
                .userId(USER_ID)
                .gmailSearchQuery("query1")
                .gmailConnected(true)
                .pollIntervalMinutes(null) // null setting should fall back to 10 min
                .lastSyncedAt(Instant.now().minus(java.time.Duration.ofMinutes(5)))
                .build();

        when(userSettingsRepository.findByGmailConnectedTrue()).thenReturn(List.of(userNullInterval));

        ingestionService.scheduledPoll();

        // 5 minutes elapsed < 10 minutes fallback -> should skip
        verify(gmailService, never()).listMessages(anyString(), anyString(), anyLong());
    }
}
