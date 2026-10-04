package com.caliber.controller;

import com.caliber.dto.JobEmailDto;
import com.caliber.dto.JobStatsDto;
import com.caliber.dto.SendReplyRequest;
import com.caliber.dto.SyncResultDto;
import com.caliber.dto.UpdateEmploymentTypeRequest;
import com.caliber.service.JobEmailService;
import com.caliber.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobEmailControllerTest {

    @Mock
    private JobEmailService jobEmailService;

    @Mock
    private UserService userService;

    @InjectMocks
    private JobEmailController jobEmailController;

    @Test
    void listJobs_DelegatesToService() {
        Page<JobEmailDto> mockPage = new PageImpl<>(List.of(JobEmailDto.builder().id("1").build()));
        when(jobEmailService.listJobs("c2c", null, null, 0, 20)).thenReturn(mockPage);

        ResponseEntity<Page<JobEmailDto>> response = jobEmailController.listJobs("c2c", null, null, 0, 20);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        verify(jobEmailService).listJobs("c2c", null, null, 0, 20);
    }

    @Test
    void getJobById_DelegatesToService() {
        JobEmailDto mockDto = JobEmailDto.builder().id("123").build();
        when(jobEmailService.getJobById("123")).thenReturn(mockDto);

        ResponseEntity<JobEmailDto> response = jobEmailController.getJobById("123");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("123", response.getBody().getId());
        verify(jobEmailService).getJobById("123");
    }

    @Test
    void getReplyDraft_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        com.caliber.dto.JobReplyDraftDto draftDto = com.caliber.dto.JobReplyDraftDto.builder().subject("Re: Job").build();
        when(jobEmailService.getReplyDraft("123", "APPLY", "user-1")).thenReturn(draftDto);

        ResponseEntity<com.caliber.dto.JobReplyDraftDto> response = jobEmailController.getReplyDraft("123", "APPLY");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Re: Job", response.getBody().subject());
        verify(jobEmailService).getReplyDraft("123", "APPLY", "user-1");
    }

    @Test
    void syncJobs_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        SyncResultDto resultDto = SyncResultDto.builder().messagesScanned(5).build();
        when(jobEmailService.syncJobs("user-1")).thenReturn(resultDto);

        ResponseEntity<SyncResultDto> response = jobEmailController.syncJobs();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().getMessagesScanned());
        verify(jobEmailService).syncJobs("user-1");
    }

    @Test
    void sendReply_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        SendReplyRequest request = new SendReplyRequest();
        JobEmailDto mockDto = JobEmailDto.builder().id("123").build();
        when(jobEmailService.sendReply("123", request, "user-1")).thenReturn(mockDto);

        ResponseEntity<JobEmailDto> response = jobEmailController.sendReply("123", request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("123", response.getBody().getId());
        verify(jobEmailService).sendReply("123", request, "user-1");
    }

    @Test
    void dismissJob_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        JobEmailDto mockDto = JobEmailDto.builder().id("123").build();
        when(jobEmailService.dismissJob("123", "user-1")).thenReturn(mockDto);

        ResponseEntity<JobEmailDto> response = jobEmailController.dismissJob("123");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("123", response.getBody().getId());
        verify(jobEmailService).dismissJob("123", "user-1");
    }

    @Test
    void updateEmploymentType_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        UpdateEmploymentTypeRequest request = new UpdateEmploymentTypeRequest("C2C");
        JobEmailDto mockDto = JobEmailDto.builder().id("123").build();
        when(jobEmailService.updateEmploymentType("123", "C2C", "user-1")).thenReturn(mockDto);

        ResponseEntity<JobEmailDto> response = jobEmailController.updateEmploymentType("123", request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("123", response.getBody().getId());
        verify(jobEmailService).updateEmploymentType("123", "C2C", "user-1");
    }

    @Test
    void getStats_DelegatesToService() {
        JobStatsDto stats = JobStatsDto.builder().total(100).build();
        when(jobEmailService.getStats()).thenReturn(stats);

        ResponseEntity<JobStatsDto> response = jobEmailController.getStats();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100, response.getBody().getTotal());
        verify(jobEmailService).getStats();
    }
}
