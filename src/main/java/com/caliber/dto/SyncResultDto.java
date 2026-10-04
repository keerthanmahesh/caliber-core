package com.caliber.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultDto {
    private int messagesScanned;
    private int newEmailsIngested;
    private int c2cCount;
    private int c2hCount;
    private int w2Count;
    private int fullTimeCount;
    private int unspecifiedCount;
    private int duplicatesSkipped;
    private int threadsUpdated;
    private Instant syncedAt;
    private String message;
    private boolean success;
}
