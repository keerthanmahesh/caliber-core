package com.caliber.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendReplyRequest {

    /**
     * Type of reply: "INQUIRY", "APPLY", or "CUSTOM"
     */
    @Builder.Default
    private String replyType = "CUSTOM";

    private String recipientEmail;

    private String subject;

    @NotBlank(message = "Email body cannot be empty")
    private String body;

    @Builder.Default
    private boolean attachResume = false;

    @Builder.Default
    private boolean archiveFromInbox = true;
}
