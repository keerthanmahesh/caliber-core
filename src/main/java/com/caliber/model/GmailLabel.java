package com.caliber.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Unified enumeration of Gmail label paths managed by Caliber.
 */
@Getter
@RequiredArgsConstructor
public enum GmailLabel {

    // Employment Type Labels in Gmail
    C2C("Jobs/C2C"),
    C2H("Jobs/C2H"),
    W2("Jobs/W2"),
    FULL_TIME("Jobs/Full-Time"),
    UNSPECIFIED("Jobs/Unspecified"),

    // Action Labels in Gmail
    INQUIRED("Jobs/Inquired"),
    APPLIED("Jobs/Applied"),
    DISMISSED("Jobs/Dismissed");

    private final String labelValue;

    public String getValue() {
        return labelValue;
    }

    @Override
    public String toString() {
        return labelValue;
    }
}
