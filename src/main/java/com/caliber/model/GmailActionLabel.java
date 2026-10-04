package com.caliber.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Gmail labels representing actions taken on recruiter job emails.
 */
@Getter
@RequiredArgsConstructor
public enum GmailActionLabel {

    INQUIRED("Jobs/Inquired"),
    APPLIED("Jobs/Applied"),
    DISMISSED("Jobs/Dismissed");

    private final String labelValue;

    public String getValue() {
        return labelValue;
    }

    public ApplicationStatus toApplicationStatus() {
        return switch (this) {
            case INQUIRED -> ApplicationStatus.INQUIRED;
            case APPLIED -> ApplicationStatus.APPLIED;
            case DISMISSED -> ApplicationStatus.DISMISSED;
        };
    }

    @Override
    public String toString() {
        return labelValue;
    }
}
