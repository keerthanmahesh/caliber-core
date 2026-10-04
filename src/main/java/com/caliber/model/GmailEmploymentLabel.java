package com.caliber.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Gmail labels representing employment / contract types.
 */
@Getter
@RequiredArgsConstructor
public enum GmailEmploymentLabel {

    C2C("Jobs/C2C"),
    C2H("Jobs/C2H"),
    W2("Jobs/W2"),
    FULL_TIME("Jobs/Full-Time"),
    UNSPECIFIED("Jobs/Unspecified");

    private final String labelValue;

    public String getValue() {
        return labelValue;
    }

    public static boolean isEmploymentLabel(String labelValue) {
        if (labelValue == null || labelValue.isBlank()) {
            return false;
        }
        for (GmailEmploymentLabel label : values()) {
            if (label.getLabelValue().equalsIgnoreCase(labelValue.trim())) {
                return true;
            }
        }
        return false;
    }

    public EmploymentType toEmploymentType() {
        return switch (this) {
            case C2C -> EmploymentType.C2C;
            case C2H -> EmploymentType.C2H;
            case W2 -> EmploymentType.W2;
            case FULL_TIME -> EmploymentType.FULL_TIME;
            case UNSPECIFIED -> EmploymentType.UNSPECIFIED;
        };
    }

    @Override
    public String toString() {
        return labelValue;
    }
}
