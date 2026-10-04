package com.caliber.dto;

import com.caliber.model.EmploymentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiReplyClassification {

    private EmploymentType employmentType;
    private String rate;
    private String reasoning;

    @JsonProperty("employmentType")
    public void setEmploymentTypeFromJson(Object value) {
        if (value instanceof EmploymentType et) {
            this.employmentType = et;
        } else if (value instanceof String str) {
            String trimmed = str.trim().toUpperCase();
            try {
                this.employmentType = EmploymentType.valueOf(trimmed);
            } catch (Exception e) {
                if (trimmed.contains("C2C") || trimmed.contains("CORP") || trimmed.contains("CONFIRM")) {
                    this.employmentType = EmploymentType.C2C;
                } else if (trimmed.contains("W2")) {
                    this.employmentType = EmploymentType.W2;
                } else if (trimmed.contains("C2H") || trimmed.contains("HIRE")) {
                    this.employmentType = EmploymentType.C2H;
                } else if (trimmed.contains("FULL") || trimmed.contains("DIRECT") || trimmed.contains("FTE")) {
                    this.employmentType = EmploymentType.FULL_TIME;
                } else {
                    this.employmentType = EmploymentType.UNSPECIFIED;
                }
            }
        } else {
            this.employmentType = EmploymentType.UNSPECIFIED;
        }
    }
}
