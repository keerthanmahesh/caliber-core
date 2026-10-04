package com.caliber.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiExtractedJob {
    private String recruiterName;
    private String jobTitle;
    private String clientOrCompany;
    private String c2cStatus; // CONFIRMED | UNSPECIFIED | W2_ONLY | C2H | FULL_TIME | OTHER
    private String employmentType; // C2C | W2 | C2H | FULL_TIME | UNSPECIFIED
    private String rate;
    private String locationType; // Remote | Hybrid | Onsite | Unspecified
    private String location;

    @Builder.Default
    private List<String> primarySkills = new ArrayList<>();

    private String summary;
}
