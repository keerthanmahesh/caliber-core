package com.caliber.dto;

import lombok.Builder;

/**
 * Pre-generated draft for replying to a job email.
 */
@Builder
public record JobReplyDraftDto(
        String subject,
        String recipientEmail,
        String recipientName,
        String body,
        boolean defaultAttachResume
) {}
