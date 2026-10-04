package com.caliber.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatsDto {
    private long total;
    private long c2c;
    private long c2h;
    private long w2;
    private long fullTime;
    private long unspecified;
    private long confirmedC2c;
    private long pending;
    private long inquired;
    private long applied;
    private long dismissed;
}
