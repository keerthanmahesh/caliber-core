package com.caliber.service;

import com.caliber.constant.AppConstants;
import com.caliber.dto.JobEmailDto;
import com.caliber.dto.JobStatsDto;
import com.caliber.dto.SendReplyRequest;
import com.caliber.dto.SyncResultDto;
import com.caliber.model.*;
import com.caliber.repository.JobEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobEmailServiceTest {

    @Mock
    private JobEmailRepository jobEmailRepository;

    @Mock
    private IngestionService ingestionService;

    @Mock
    private GmailService gmailService;

    @Mock
    private GmailAuthService gmailAuthService;

    @Mock
    private ResumeService resumeService;

    @Mock
    private SettingsService settingsService;

    private JobEmailService jobEmailService;

    @BeforeEach
    void setUp() {
        jobEmailService = new JobEmailService(
                jobEmailRepository,
                ingestionService,
                gmailService,
                gmailAuthService,
                resumeService,
                settingsService
        );
    }

    @Test
    void listJobs_WithSearchQuery_CallsSearchAll() {
        Page<JobEmail> page = new PageImpl<>(List.of(createSampleEmail("1")));
        when(jobEmailRepository.searchAll(eq("Java"), any(Pageable.class))).thenReturn(page);

        Page<JobEmailDto> result = jobEmailService.listJobs(null, null, "Java", 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(jobEmailRepository).searchAll(eq("Java"), any(Pageable.class));
    }

    @Test
    void listJobs_WithTabW2_CallsFindByEmploymentTypeW2() {
        Page<JobEmail> page = new PageImpl<>(List.of(createSampleEmail("2")));
        when(jobEmailRepository.findByEmploymentType(eq(EmploymentType.W2), any(Pageable.class))).thenReturn(page);

        Page<JobEmailDto> result = jobEmailService.listJobs("w2", null, null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(jobEmailRepository).findByEmploymentType(eq(EmploymentType.W2), any(Pageable.class));
    }

    @Test
    void listJobs_WithTabC2C_CallsFindByEmploymentTypeC2C() {
        Page<JobEmail> page = new PageImpl<>(List.of(createSampleEmail("3")));
        when(jobEmailRepository.findByEmploymentType(eq(EmploymentType.C2C), any(Pageable.class))).thenReturn(page);

        Page<JobEmailDto> result = jobEmailService.listJobs("c2c", null, null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(jobEmailRepository).findByEmploymentType(eq(EmploymentType.C2C), any(Pageable.class));
    }

    @Test
    void listJobs_WithTabFullTime_CallsFindByEmploymentTypeFullTime() {
        Page<JobEmail> page = new PageImpl<>(List.of(createSampleEmail("3b")));
        when(jobEmailRepository.findByEmploymentType(eq(EmploymentType.FULL_TIME), any(Pageable.class))).thenReturn(page);

        Page<JobEmailDto> result = jobEmailService.listJobs("full_time", null, null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(jobEmailRepository).findByEmploymentType(eq(EmploymentType.FULL_TIME), any(Pageable.class));
    }

    @Test
    void listJobs_WithTabHistory_CallsFindByApplicationStatusIn() {
        Page<JobEmail> page = new PageImpl<>(List.of(createSampleEmail("4")));
        when(jobEmailRepository.findByApplicationStatusIn(anyList(), any(Pageable.class))).thenReturn(page);

        Page<JobEmailDto> result = jobEmailService.listJobs("history", "ACTED", null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(jobEmailRepository).findByApplicationStatusIn(anyList(), any(Pageable.class));
    }

    @Test
    void getJobById_WhenFound_ReturnsDto() {
        JobEmail email = createSampleEmail("job-100");
        when(jobEmailRepository.findById("job-100")).thenReturn(Optional.of(email));

        JobEmailDto dto = jobEmailService.getJobById("job-100");

        assertNotNull(dto);
        assertEquals("job-100", dto.getId());
    }

    @Test
    void getJobById_WhenNotFound_ThrowsException() {
        when(jobEmailRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> jobEmailService.getJobById("missing"));
    }

    @Test
    void getReplyDraft_GeneratesApplicationDraftForC2c() {
        JobEmail email = createSampleEmail("job-1");
        email.setEmploymentType(EmploymentType.C2C);
        email.setSubject("Senior Java Lead");
        email.setSenderEmail("recruiter@example.com");
        email.setSenderName("Recruiter");

        when(jobEmailRepository.findById("job-1")).thenReturn(Optional.of(email));

        UserSettings settings = UserSettings.builder()
                .applicationTemplate("App Template")
                .inquiryTemplate("Inq Template")
                .build();
        when(settingsService.getOrCreateSettings("user-1")).thenReturn(settings);
        when(settingsService.renderTemplate(eq("App Template"), eq(email), eq("Candidate"))).thenReturn("Rendered body");

        com.caliber.dto.JobReplyDraftDto draft = jobEmailService.getReplyDraft("job-1", null, "user-1");

        assertEquals("Re: Senior Java Lead", draft.subject());
        assertEquals("recruiter@example.com", draft.recipientEmail());
        assertEquals("Recruiter", draft.recipientName());
        assertEquals("Rendered body", draft.body());
        assertTrue(draft.defaultAttachResume());
    }

    @Test
    void syncJobs_DelegatesToIngestionService() {
        SyncResultDto mockResult = SyncResultDto.builder().messagesScanned(10).newEmailsIngested(5).build();
        when(ingestionService.syncNow("user-1")).thenReturn(mockResult);

        SyncResultDto result = jobEmailService.syncJobs("user-1");

        assertNotNull(result);
        assertEquals(10, result.getMessagesScanned());
        assertEquals(5, result.getNewEmailsIngested());
        verify(ingestionService).syncNow("user-1");
    }

    @Test
    void sendReply_SendsInquiryAndUpdatesEntity() throws Exception {
        JobEmail email = createSampleEmail("job-1");
        when(jobEmailRepository.findById("job-1")).thenReturn(Optional.of(email));
        when(jobEmailRepository.save(any(JobEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(gmailService.sendReply(eq("user-1"), eq(email), eq("Re: Opportunity"), eq("Body text"), isNull(), eq("Jobs/Inquired"), eq(true)))
                .thenReturn("sent-msg-123");

        SendReplyRequest request = new SendReplyRequest();
        request.setReplyType("INQUIRY");
        request.setSubject("Re: Opportunity");
        request.setBody("Body text");
        request.setAttachResume(false);
        request.setArchiveFromInbox(true);

        JobEmailDto dto = jobEmailService.sendReply("job-1", request, "user-1");

        assertNotNull(dto);
        assertEquals(ApplicationStatus.INQUIRED, dto.getApplicationStatus());
        assertEquals("sent-msg-123", dto.getReplyMessageId());
        assertTrue(dto.getGmailLabels().contains("Jobs/Inquired"));
    }

    @Test
    void dismissJob_SetsDismissedStatusAndAddsDismissedLabel() {
        JobEmail email = createSampleEmail("job-1");
        when(jobEmailRepository.findById("job-1")).thenReturn(Optional.of(email));
        when(jobEmailRepository.save(any(JobEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gmailAuthService.isConfigured("user-1")).thenReturn(false);

        JobEmailDto dto = jobEmailService.dismissJob("job-1", "user-1");

        assertNotNull(dto);
        assertEquals(ApplicationStatus.DISMISSED, dto.getApplicationStatus());
        assertTrue(dto.getGmailLabels().contains(GmailActionLabel.DISMISSED.getLabelValue()));
    }

    @Test
    void updateEmploymentType_ValidType_UpdatesTypeAndStatus() {
        JobEmail email = createSampleEmail("job-1");
        email.setEmploymentType(EmploymentType.UNSPECIFIED);

        when(jobEmailRepository.findById("job-1")).thenReturn(Optional.of(email));
        when(jobEmailRepository.save(any(JobEmail.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gmailAuthService.isConfigured("user-1")).thenReturn(false);

        JobEmailDto dto = jobEmailService.updateEmploymentType("job-1", "W2", "user-1");

        assertNotNull(dto);
        assertEquals(EmploymentType.W2, dto.getEmploymentType());
        assertEquals(EmploymentType.W2, dto.getEmploymentStatus());
    }

    @Test
    void updateEmploymentType_BlankType_ThrowsBadRequest() {
        JobEmail email = createSampleEmail("job-1");
        when(jobEmailRepository.findById("job-1")).thenReturn(Optional.of(email));

        assertThrows(ResponseStatusException.class, () -> jobEmailService.updateEmploymentType("job-1", "", "user-1"));
    }

    @Test
    void getStats_AggregatesCountsCorrectly() {
        JobStatsDto mockStats = JobStatsDto.builder()
                .total(100L)
                .c2c(40L)
                .c2h(10L)
                .w2(20L)
                .fullTime(15L)
                .unspecified(15L)
                .confirmedC2c(40L)
                .pending(50L)
                .inquired(20L)
                .applied(15L)
                .dismissed(15L)
                .build();
        when(jobEmailRepository.getAggregatedStats()).thenReturn(Optional.of(mockStats));

        JobStatsDto stats = jobEmailService.getStats();

        assertNotNull(stats);
        assertEquals(100L, stats.getTotal());
        assertEquals(40L, stats.getC2c());
        assertEquals(10L, stats.getC2h());
        assertEquals(20L, stats.getW2());
        assertEquals(15L, stats.getFullTime());
        assertEquals(50L, stats.getPending());
        verify(jobEmailRepository).getAggregatedStats();
    }

    @Test
    void getStats_WhenEmpty_ReturnsDefaultStats() {
        when(jobEmailRepository.getAggregatedStats()).thenReturn(Optional.empty());

        JobStatsDto stats = jobEmailService.getStats();

        assertNotNull(stats);
        assertEquals(0L, stats.getTotal());
        assertEquals(0L, stats.getC2c());
        verify(jobEmailRepository).getAggregatedStats();
    }

    private JobEmail createSampleEmail(String id) {
        return JobEmail.builder()
                .id(id)
                .messageId("msg-" + id)
                .threadId("thd-" + id)
                .senderEmail("test@example.com")
                .senderName("Tester")
                .subject("Software Engineer")
                .gmailLabels(new ArrayList<>())
                .applicationStatus(ApplicationStatus.PENDING)
                .employmentType(EmploymentType.UNSPECIFIED)
                .build();
    }
}
