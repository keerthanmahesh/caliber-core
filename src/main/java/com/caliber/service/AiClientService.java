package com.caliber.service;

import com.caliber.config.AiConfig;
import com.caliber.constant.AppConstants;
import com.caliber.dto.AiExtractedJob;
import com.caliber.dto.AiReplyClassification;
import com.caliber.exception.AiConfigurationException;
import com.caliber.exception.AiExtractionException;
import com.caliber.model.EmploymentType;
import com.caliber.model.UserSettings;
import com.caliber.repository.UserSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.caliber.constant.AppConstants.EMPTY_STRING;

/**
 * Service for structured job classification and data extraction using LLMs.
 * <p>
 * Implements primary extraction via Ollama with automatic resilient
 * fallback to Google Gemini (e.g. Gemini 1.5/2.0 Flash).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiClientService {

    private final AiConfig aiConfig;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are an expert recruiter assistant and job email classifier.
            Analyze the following email from a recruiter or employer about a job opportunity.
            Extract the requested information accurately into a valid JSON object matching this schema:
            {
              "recruiterName": "Recruiter's full name or null/empty if unknown",
              "jobTitle": "Specific job title (e.g. Senior Java Backend Developer)",
              "company": "Staffing agency, recruiter's company, or employer company name (e.g. Siri InfoSolutions Inc.)",
              "client": "End client name if mentioned (e.g. Apple, Wells Fargo, Cisco) or null if unknown or direct hire",
              "clientOrCompany": "Staffing agency or end client",
              "employmentType": "C2C | W2 | C2H | FULL_TIME | UNSPECIFIED",
              "rate": "Hourly rate, salary or 'Not Mentioned'",
              "locationType": "Remote | Hybrid | Onsite | Unspecified",
              "location": "City, State or 'Remote'",
              "primarySkills": ["Skill 1", "Skill 2", "Skill 3"],
              "summary": "Concise 2-3 sentence overview of the role and key requirements"
            }

            Rules for employmentType:
            - "C2C": Email explicitly mentions C2C, Corp-to-Corp, Corp2Corp, C-to-C, or states 1099/business contract is accepted.
            - "W2": Email explicitly says "W2 only", "W-2 only", "No C2C", "US Citizens & GC only", "Only US Citizens", or direct W2 employee.
            - "C2H": Explicitly Contract-to-Hire.
            - "FULL_TIME": Explicitly Full-Time permanent direct hire / FTE.
            - "UNSPECIFIED": Email is a tech role opening but DOES NOT explicitly mention tax terms or contract eligibility, or not a job opening.

            Return ONLY raw JSON, with no markdown code blocks and no surrounding commentary.
            """;

    private static final String REPLY_CLASSIFICATION_PROMPT = """
            You are an expert AI recruiter assistant and contract type classifier.
            A candidate previously inquired about a job opening, asking if the role is open for Corp-to-Corp (C2C) contract terms.
            The recruiter has now replied to the inquiry.

            Carefully evaluate the recruiter's reply in the context of the inquiry to determine the contract/employment type:
            - "C2C": The recruiter confirms, agrees, or indicates C2C is accepted.
              Examples: "Yes, we can do C2C", "C2C is fine", "Sure, what is your rate?", "Yes, send the profile/resume", "Yes", "Confirmed for corp-to-corp", "Our client allows 1099/C2C", "Yes, please share candidate details".
            - "W2": The recruiter rejects C2C, specifies W2 only, or states direct employment only.
              Examples: "No C2C", "W2 only", "We cannot do corp to corp", "Direct W2 only", "Only USC/GC on our W2", "No third party candidates".
            - "C2H": The recruiter states the position is Contract-to-Hire.
              Examples: "This is C2H only", "Contract to hire after 6 months".
            - "FULL_TIME": The recruiter states the position is permanent direct hire / FTE.
              Examples: "Full time only", "Direct hire permanent role".
            - "UNSPECIFIED": The recruiter's reply is ambiguous, asks for more info without confirming contract type, is an automated out-of-office response, or does not clearly state whether C2C is accepted or rejected.
              Examples: "Let me check with the account manager", "Can you send the candidate's phone number?", "Out of office", "Thank you, will get back to you".

            Return ONLY raw JSON matching this schema:
            {
              "employmentType": "C2C" | "W2" | "C2H" | "FULL_TIME" | "UNSPECIFIED",
              "rate": "Extracted rate if mentioned (e.g. $80/hr) or null",
              "reasoning": "Brief explanation why"
            }
            """;

    private static final Pattern C2C_REPLY_PATTERN = Pattern.compile(
            "\\b(c2c|corp[- ]to[- ]corp|c-to-c|corp2corp|corp 2 corp|1099|yes|sure|agreed|accepted|fine with c2c|open for c2c|c2c is fine|c2c works)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern W2_REPLY_PATTERN = Pattern.compile(
            "\\b(w2 only|w-2 only|no c2c|no corp[- ]to[- ]corp|us citizens? and gc only|us citizens? only|citizens? or gc only|only w2|w2 position only|cannot do c2c|not open for c2c|no third part(?:y|ies))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern C2H_REPLY_PATTERN = Pattern.compile(
            "\\b(c2h|contract[- ]to[- ]hire|contract 2 hire|contract to perm)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FULL_TIME_REPLY_PATTERN = Pattern.compile(
            "\\b(full[- ]time only|direct hire only|direct hire|fte position|full-time permanent)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts structured job details using Ollama with fallback to Gemini.
     */
    public AiExtractedJob extractJobDetails(String emailSubject, String emailBody, String userId) {
        String ollamaKey = resolveOllamaKey(userId);
        String geminiKey = resolveGeminiKey(userId);

        String combinedEmail = "SUBJECT: " + emailSubject + "\n\nBODY:\n" + truncateBody(emailBody, resolveMaxBodyChars());

        // 1. Try Ollama (Primary)
        if (isOllamaConfigured(userId)) {
            try {
                log.info("Attempting extraction via Ollama ({})", aiConfig.getOllama().getModel());
                return callOllama(ollamaKey, combinedEmail);
            } catch (Exception ex) {
                log.warn("Ollama extraction failed: {}. Attempting fallback to Gemini...", ex.getMessage());
            }
        } else {
            log.debug("Ollama not configured, checking Gemini fallback...");
        }

        // 2. Try Gemini (Fallback)
        if (geminiKey != null && !geminiKey.isBlank()) {
            try {
                log.info("Attempting extraction via Gemini ({})", aiConfig.getGemini().getModel());
                return callGemini(geminiKey, combinedEmail);
            } catch (Exception ex) {
                log.error("Gemini extraction failed: {}", ex.getMessage(), ex);
                throw new AiExtractionException(AppConstants.ERR_AI_EXTRACTION_FAILED_PREFIX + ex.getMessage(), ex);
            }
        }

        throw new AiConfigurationException(AppConstants.ERR_AI_NOT_CONFIGURED);
    }

    private AiExtractedJob callOllama(String apiKey, String content) {
        return executeOllamaChat(apiKey, SYSTEM_PROMPT, content, AiExtractedJob.class);
    }

    private AiExtractedJob callGemini(String apiKey, String content) {
        return executeGeminiGenerate(apiKey, SYSTEM_PROMPT, content, 1000, AiExtractedJob.class);
    }

    private <T> T executeOllamaChat(String apiKey, String systemPrompt, String content, Class<T> clazz) {
        try {
            String endpoint = resolveOllamaEndpoint();
            RestClient restClient = buildOllamaRestClient(apiKey);

            Map<String, Object> requestPayload = Map.of(
                    "model", aiConfig.getOllama().getModel(),
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", content)
                    ),
                    "temperature", 0.1,
                    "stream", false
            );

            String responseJson = restClient.post()
                    .uri(endpoint)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            String textContent = extractOllamaTextContent(responseJson);
            return parseCleanJson(textContent, clazz);
        } catch (AiExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new AiExtractionException("Ollama chat completion failed: " + e.getMessage(), e);
        }
    }

    private String resolveOllamaEndpoint() {
        String baseUrl = aiConfig.getOllama().getBaseUrl();
        String trimmedUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://ollama.com" : baseUrl.trim();
        while (trimmedUrl.endsWith("/")) {
            trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
        }

        if (trimmedUrl.endsWith("/v1/chat/completions") || trimmedUrl.endsWith("/api/chat")) {
            return trimmedUrl;
        }
        if (trimmedUrl.endsWith("/v1")) {
            return trimmedUrl + "/chat/completions";
        }
        return trimmedUrl + "/v1/chat/completions";
    }

    private RestClient buildOllamaRestClient(String apiKey) {
        SimpleClientHttpRequestFactory ollamaRequestFactory = new SimpleClientHttpRequestFactory();
        int ollamaTimeout = aiConfig.getOllama().getTimeoutSeconds() > 0 ? aiConfig.getOllama().getTimeoutSeconds() : 60;
        ollamaRequestFactory.setConnectTimeout(Duration.ofSeconds(ollamaTimeout));
        ollamaRequestFactory.setReadTimeout(Duration.ofSeconds(ollamaTimeout));

        var clientBuilder = RestClient.builder()
                .requestFactory(ollamaRequestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }

        return clientBuilder.build();
    }

    private String extractOllamaTextContent(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String textContent = null;
            if (root.has("choices") && !root.path("choices").isEmpty()) {
                textContent = root.path("choices").path(0).path("message").path("content").asText();
            } else if (root.has("message") && root.path("message").has("content")) {
                textContent = root.path("message").path("content").asText();
            } else if (root.has("response")) {
                textContent = root.path("response").asText();
            }

            if (textContent == null || textContent.isBlank()) {
                throw new AiExtractionException("Empty or unrecognized response structure from Ollama: " + responseJson);
            }

            return textContent;
        } catch (JsonProcessingException e) {
            throw new AiExtractionException("Failed to parse Ollama JSON response: " + e.getMessage(), e);
        }
    }

    private <T> T executeGeminiGenerate(String apiKey, String systemPrompt, String content, int maxOutputTokens, Class<T> clazz) {
        try {
            String url = String.format("%s/%s:generateContent?key=%s",
                    aiConfig.getGemini().getBaseUrl(),
                    aiConfig.getGemini().getModel(),
                    apiKey);

            SimpleClientHttpRequestFactory geminiRequestFactory = new SimpleClientHttpRequestFactory();
            int geminiTimeout = aiConfig.getGemini().getTimeoutSeconds() > 0 ? aiConfig.getGemini().getTimeoutSeconds() : 25;
            geminiRequestFactory.setConnectTimeout(Duration.ofSeconds(geminiTimeout));
            geminiRequestFactory.setReadTimeout(Duration.ofSeconds(geminiTimeout));

            RestClient restClient = RestClient.builder()
                    .requestFactory(geminiRequestFactory)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", systemPrompt + "\n\nEMAIL CONTENT:\n" + content)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "response_mime_type", "application/json",
                            "temperature", 0.1,
                            "maxOutputTokens", maxOutputTokens
                    )
            );

            String responseJson = restClient.post()
                    .uri(url)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            String textContent = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
            return parseCleanJson(textContent, clazz);
        } catch (AiExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new AiExtractionException("Gemini generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Classifies a recruiter's response to a candidate's C2C inquiry.
     * Determines whether the recruiter agreed to C2C, restricted to W2/C2H/Full-Time,
     * or if the response remains Unspecified.
     */
    public AiReplyClassification classifyRecruiterReply(String jobTitle, String candidateInquiry, String recruiterReply, String userId) {
        String ollamaKey = resolveOllamaKey(userId);
        String geminiKey = resolveGeminiKey(userId);

        String promptContent = String.format(
                "JOB TITLE: %s%n%nCANDIDATE'S INQUIRY (Asked if open for C2C):%n%s%n%nRECRUITER'S REPLY:%n%s",
                (jobTitle != null && !jobTitle.isBlank()) ? jobTitle : "Not Specified",
                (candidateInquiry != null && !candidateInquiry.isBlank()) ? candidateInquiry : "Asked if this role is open for Corp-to-Corp (C2C)",
                truncateBody(recruiterReply, resolveMaxBodyChars())
        );

        if (isOllamaConfigured(userId)) {
            try {
                log.info("Attempting reply classification via Ollama ({})", aiConfig.getOllama().getModel());
                return callOllamaForReply(ollamaKey, promptContent);
            } catch (Exception ex) {
                log.warn("Ollama reply classification failed: {}. Attempting fallback to Gemini...", ex.getMessage());
            }
        }

        if (geminiKey != null && !geminiKey.isBlank()) {
            try {
                log.info("Attempting reply classification via Gemini ({})", aiConfig.getGemini().getModel());
                return callGeminiForReply(geminiKey, promptContent);
            } catch (Exception ex) {
                log.warn("Gemini reply classification failed: {}. Falling back to regex heuristics.", ex.getMessage());
            }
        }

        return fallbackReplyClassification(recruiterReply);
    }

    private AiReplyClassification callOllamaForReply(String apiKey, String content) {
        return executeOllamaChat(apiKey, REPLY_CLASSIFICATION_PROMPT, content, AiReplyClassification.class);
    }

    private AiReplyClassification callGeminiForReply(String apiKey, String content) {
        return executeGeminiGenerate(apiKey, REPLY_CLASSIFICATION_PROMPT, content, 500, AiReplyClassification.class);
    }

    private AiReplyClassification fallbackReplyClassification(String recruiterReply) {
        if (recruiterReply == null || recruiterReply.isBlank()) {
            return AiReplyClassification.builder().employmentType(EmploymentType.UNSPECIFIED).reasoning("Empty reply").build();
        }
        if (W2_REPLY_PATTERN.matcher(recruiterReply).find()) {
            return AiReplyClassification.builder().employmentType(EmploymentType.W2).reasoning("Regex fallback: Matched W2 only / No C2C").build();
        }
        if (C2C_REPLY_PATTERN.matcher(recruiterReply).find()) {
            return AiReplyClassification.builder().employmentType(EmploymentType.C2C).reasoning("Regex fallback: Matched C2C positive").build();
        }
        if (C2H_REPLY_PATTERN.matcher(recruiterReply).find()) {
            return AiReplyClassification.builder().employmentType(EmploymentType.C2H).reasoning("Regex fallback: Matched C2H").build();
        }
        if (FULL_TIME_REPLY_PATTERN.matcher(recruiterReply).find()) {
            return AiReplyClassification.builder().employmentType(EmploymentType.FULL_TIME).reasoning("Regex fallback: Matched Full-Time").build();
        }
        return AiReplyClassification.builder().employmentType(EmploymentType.UNSPECIFIED).reasoning("Regex fallback: Unspecified / Ambiguous").build();
    }

    private <T> T parseCleanJson(String raw, Class<T> clazz) {
        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        clean = clean.trim();
        try {
            return objectMapper.readValue(clean, clazz);
        } catch (JsonProcessingException e) {
            throw new AiExtractionException("Failed to deserialize AI response JSON: " + e.getMessage(), e);
        }
    }

    private String truncateBody(String body, int maxLength) {
        if (body == null) return EMPTY_STRING;
        return body.length() <= maxLength ? body : body.substring(0, maxLength);
    }

    private int resolveMaxBodyChars() {
        if (aiConfig != null && aiConfig.getMaxBodyChars() > 0) {
            return aiConfig.getMaxBodyChars();
        }
        return AppConstants.DEFAULT_MAX_BODY_CHARS;
    }

    private String resolveOllamaKey(String userId) {
        if (userId != null) {
            Optional<UserSettings> settings = userSettingsRepository.findByUserId(userId);
            if (settings.isPresent() && settings.get().getOllamaApiKey() != null && !settings.get().getOllamaApiKey().isBlank()) {
                return settings.get().getOllamaApiKey();
            }
        }
        return aiConfig.getOllama().getApiKey();
    }

    private boolean isOllamaConfigured(String userId) {
        String key = resolveOllamaKey(userId);
        return (key != null && !key.isBlank()) ||
                (aiConfig.getOllama().getBaseUrl() != null && !aiConfig.getOllama().getBaseUrl().isBlank());
    }

    private String resolveGeminiKey(String userId) {
        if (userId != null) {
            Optional<UserSettings> settings = userSettingsRepository.findByUserId(userId);
            if (settings.isPresent() && settings.get().getGeminiApiKey() != null && !settings.get().getGeminiApiKey().isBlank()) {
                return settings.get().getGeminiApiKey();
            }
        }
        return aiConfig.getGemini().getApiKey();
    }
}
