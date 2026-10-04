package com.caliber.service;

import com.caliber.dto.AiExtractedJob;
import com.caliber.model.ApplicationStatus;
import com.caliber.model.EmploymentType;
import com.caliber.model.JobEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid classification pipeline combining deterministic regex pre-filtering with structured LLM parsing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationEngine {

    private final AiClientService aiClientService;

    // Regex Pre-Filters
    private static final Pattern W2_PATTERN = Pattern.compile(
            "\\b(w2 only|w-2 only|no c2c|no corp[- ]to[- ]corp|us citizens? and gc only|us citizens? only|citizens? or gc only|only w2|w2 position only|cannot do c2c|not open for c2c|no third part(?:y|ies))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern C2C_PATTERN = Pattern.compile(
            "\\b(c2c|corp[- ]to[- ]corp|c-to-c|corp2corp|corp 2 corp|1099 eligible|c2c accepted|open for c2c|c2c is fine|c2c works)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern C2H_PATTERN = Pattern.compile(
            "\\b(c2h|contract[- ]to[- ]hire|contract 2 hire|contract to perm)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FULL_TIME_PATTERN = Pattern.compile(
            "\\b(full[- ]time only|direct hire only|direct hire|fte position|full-time permanent)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RATE_PATTERN = Pattern.compile(
            "(?<!\\w)(?:(?:rate|pay|salary|compensation|budget)[:\\s-]*\\s*)?(\\$?(?:USD\\s*)?\\d{2,3}(?:[.,]\\d{2})?(?:\\s*(?:/|per\\s+)?(?:hr|hour|yr|year|annum|k\\b))?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "\\b(remote|hybrid|onsite|on[- ]site)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Executes classification and structured field extraction pipeline for an ingested email.
     */
    public void classifyAndEnrich(JobEmail email, String userId) {
        String fullText = (email.getSubject() != null ? email.getSubject() : "") + " " +
                (email.getBodyText() != null ? email.getBodyText() : "");

        if (email.getEmploymentType() != null) {
            log.info("Email {} already tagged as {}. Skipping LLM classification.",
                    email.getMessageId(), email.getEmploymentType());
            applyFallbackHeuristics(email, fullText, email.getEmploymentType());
            return;
        }

        // Step 1: Deterministic Regex Pre-Filter
        EmploymentType regexType = determineRegexEmploymentType(fullText);
        log.debug("Regex pre-filter determined preliminary employment type: {}", regexType);

        // Step 2: Structured LLM Output with Ollama / Gemini
        try {
            AiExtractedJob aiResult = aiClientService.extractJobDetails(email.getSubject(), email.getBodyText(), userId);
            if (aiResult != null) {
                applyAiExtraction(email, aiResult, regexType);
                return;
            }
        } catch (Exception ex) {
            log.warn("AI extraction skipped or failed ({}). Falling back to heuristic extraction.", ex.getMessage());
        }

        // Fallback Heuristic Extraction if AI is unavailable
        applyFallbackHeuristics(email, fullText, regexType);
    }

    private EmploymentType determineRegexEmploymentType(String text) {
        if (W2_PATTERN.matcher(text).find()) {
            return EmploymentType.W2;
        }
        if (C2C_PATTERN.matcher(text).find()) {
            return EmploymentType.C2C;
        }
        if (C2H_PATTERN.matcher(text).find()) {
            return EmploymentType.C2H;
        }
        if (FULL_TIME_PATTERN.matcher(text).find()) {
            return EmploymentType.FULL_TIME;
        }
        return EmploymentType.UNSPECIFIED;
    }

    private void applyAiExtraction(JobEmail email, AiExtractedJob ai, EmploymentType regexType) {
        email.setJobTitle(resolveJobTitle(email, ai));
        populateJobMetadata(email, ai);
        populateLocationAndSkills(email, ai);
        email.setSummary(resolveSummary(email, ai));
        email.setEmploymentType(resolveAiEmploymentType(ai, regexType));
        email.setApplicationStatus(ApplicationStatus.PENDING);
    }

    private String resolveJobTitle(JobEmail email, AiExtractedJob ai) {
        return (ai.getJobTitle() != null && !ai.getJobTitle().isBlank())
                ? ai.getJobTitle()
                : cleanSubjectForTitle(email.getSubject());
    }

    private void populateJobMetadata(JobEmail email, AiExtractedJob ai) {
        if (ai.getClientOrCompany() != null && !ai.getClientOrCompany().isBlank()) {
            email.setClientOrCompany(ai.getClientOrCompany());
        }
        if (ai.getRecruiterName() != null && !ai.getRecruiterName().isBlank()) {
            email.setSenderName(ai.getRecruiterName());
        }
        if (ai.getRate() != null && !ai.getRate().isBlank()) {
            email.setRate(ai.getRate());
        }
    }

    private void populateLocationAndSkills(JobEmail email, AiExtractedJob ai) {
        if (ai.getLocationType() != null && !ai.getLocationType().isBlank()) {
            email.setLocationType(ai.getLocationType());
        }
        if (ai.getLocation() != null && !ai.getLocation().isBlank()) {
            email.setLocation(ai.getLocation());
        }
        if (ai.getPrimarySkills() != null && !ai.getPrimarySkills().isEmpty()) {
            email.setPrimarySkills(ai.getPrimarySkills());
        }
    }

    private String resolveSummary(JobEmail email, AiExtractedJob ai) {
        return (ai.getSummary() != null && !ai.getSummary().isBlank())
                ? ai.getSummary()
                : email.getSnippet();
    }

    private EmploymentType resolveAiEmploymentType(AiExtractedJob ai, EmploymentType regexType) {
        if (regexType == EmploymentType.W2 || regexType == EmploymentType.C2C) {
            return regexType;
        }
        return parseEmploymentType(ai.getEmploymentType(), ai.getC2cStatus());
    }

    private void applyFallbackHeuristics(JobEmail email, String fullText, EmploymentType regexType) {
        if (email.getEmploymentType() == null) {
            email.setEmploymentType(regexType);
        }
        if (email.getJobTitle() == null || email.getJobTitle().isBlank()) {
            email.setJobTitle(cleanSubjectForTitle(email.getSubject()));
        }

        Matcher rateMatcher = RATE_PATTERN.matcher(fullText);
        if (rateMatcher.find()) {
            email.setRate(rateMatcher.group(1).trim());
        } else if (email.getRate() == null) {
            email.setRate("Not Mentioned");
        }

        Matcher locMatcher = LOCATION_PATTERN.matcher(fullText);
        if (locMatcher.find()) {
            String loc = locMatcher.group(1).toLowerCase();
            if (loc.contains("remote")) email.setLocationType("Remote");
            else if (loc.contains("hybrid")) email.setLocationType("Hybrid");
            else email.setLocationType("Onsite");
        } else if (email.getLocationType() == null) {
            email.setLocationType("Unspecified");
        }

        if (email.getSummary() == null) {
            email.setSummary(email.getSnippet() != null ? email.getSnippet() : email.getSubject());
        }
        if (email.getApplicationStatus() == null) {
            email.setApplicationStatus(ApplicationStatus.PENDING);
        }
    }

    private EmploymentType parseEmploymentType(String typeStr, String legacyStatusStr) {
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                return EmploymentType.valueOf(typeStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (legacyStatusStr != null && !legacyStatusStr.isBlank()) {
            try {
                return EmploymentType.valueOf(legacyStatusStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                String upper = legacyStatusStr.toUpperCase();
                if (upper.contains("CONFIRM") || upper.contains("C2C")) {
                    return EmploymentType.C2C;
                }
                if (upper.contains("W2")) {
                    return EmploymentType.W2;
                }
            }
        }
        return EmploymentType.UNSPECIFIED;
    }

    private String cleanSubjectForTitle(String subject) {
        if (subject == null) return "Job Opportunity";
        return subject
                .replaceAll("(?i)^(re|fwd|urgent|immediate\\s+need|hot\\s+requirement|job\\s+opening)\\s*:\\s*", "")
                .trim();
    }
}
