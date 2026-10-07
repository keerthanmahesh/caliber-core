package com.caliber.controller;

import com.caliber.constant.AppConstants;
import com.caliber.dto.JobEmailDto;
import com.caliber.dto.JobReplyDraftDto;
import com.caliber.dto.JobStatsDto;
import com.caliber.dto.SendReplyRequest;
import com.caliber.dto.SyncResultDto;
import com.caliber.dto.UpdateEmploymentTypeRequest;
import com.caliber.service.JobEmailService;
import com.caliber.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for job email querying, synchronizing, reply generation,
 * label reconciliation, and metrics.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobEmailController {

    private final JobEmailService jobEmailService;
    private final UserService userService;

    private String resolveUserId() {
        return userService.resolveCurrentUserId();
    }

    /**
     * Lists and filters job emails based on tab, status, and search query.
     */
    @GetMapping
    public ResponseEntity<Page<JobEmailDto>> listJobs(
            @RequestParam(value = AppConstants.PARAM_TAB, defaultValue = AppConstants.TAB_C2C) String tab,
            @RequestParam(value = AppConstants.PARAM_STATUS, required = false) String status,
            @RequestParam(value = AppConstants.PARAM_SEARCH, required = false) String search,
            @RequestParam(value = AppConstants.PARAM_PAGE, defaultValue = AppConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(value = AppConstants.PARAM_SIZE, defaultValue = AppConstants.DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(jobEmailService.listJobs(tab, status, search, page, size));
    }

    /**
     * Retrieves single job email by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobEmailDto> getJobById(@PathVariable String id) {
        return ResponseEntity.ok(jobEmailService.getJobById(id));
    }

    /**
     * Pre-generates reply draft for a given job based on inquiry vs application template.
     */
    @GetMapping("/{id}/draft")
    public ResponseEntity<JobReplyDraftDto> getReplyDraft(
            @PathVariable String id,
            @RequestParam(value = AppConstants.PARAM_TYPE, required = false) String type
    ) {
        return ResponseEntity.ok(jobEmailService.getReplyDraft(id, type, resolveUserId()));
    }

    /**
     * Triggers manual synchronization with Gmail.
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResultDto> syncJobs() {
        return ResponseEntity.ok(jobEmailService.syncJobs(resolveUserId()));
    }

    /**
     * Sends a thread-preserving reply, manages Gmail labels, and updates application status.
     */
    @PostMapping("/{id}/reply")
    public ResponseEntity<JobEmailDto> sendReply(
            @PathVariable String id,
            @Valid @RequestBody SendReplyRequest request
    ) {
        return ResponseEntity.ok(jobEmailService.sendReply(id, request, resolveUserId()));
    }

    /**
     * Dismisses a job opening, marks as DISMISSED, and applies Jobs/Dismissed label in Gmail.
     */
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<JobEmailDto> dismissJob(@PathVariable String id) {
        return ResponseEntity.ok(jobEmailService.dismissJob(id, resolveUserId()));
    }

    /**
     * Manually updates the employment type / label of a job email and synchronizes Gmail labels.
     */
    @RequestMapping(value = "/{id}/employment-type", method = {RequestMethod.PATCH, RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<JobEmailDto> updateEmploymentType(
            @PathVariable String id,
            @RequestBody UpdateEmploymentTypeRequest request
    ) {
        return ResponseEntity.ok(jobEmailService.updateEmploymentType(id, request.employmentType(), resolveUserId()));
    }

    /**
     * Returns job statistics and metric counts.
     */
    @GetMapping("/stats")
    public ResponseEntity<JobStatsDto> getStats() {
        return ResponseEntity.ok(jobEmailService.getStats());
    }
}
