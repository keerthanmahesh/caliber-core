package com.caliber.service;

import com.caliber.config.GmailConfig;
import com.caliber.constant.AppConstants;
import com.caliber.exception.OAuthTokenException;
import com.caliber.model.UserSettings;
import com.caliber.repository.UserSettingsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.core.constant.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.caliber.constant.AppConstants.AMPERSAND;
import static com.caliber.constant.AppConstants.EQUALS;

/**
 * Service managing Google OAuth2 authentication flow, authorization URL generation,
 * token exchange, and authenticated Gmail client instantiation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmailAuthService {

    private final GmailConfig gmailConfig;
    private final SettingsService settingsService;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    /**
     * Retrieves the current Gmail connection status and client configuration for a user.
     *
     * @param userId internal user identifier
     * @return map with connection status, client ID, and active search query
     */
    public Map<String, Object> getStatus(String userId) {
        boolean isConnected = isConfigured(userId);
        String resolvedClientId = resolveClientId(userId);
        String searchQuery = userSettingsRepository.findByUserId(userId)
                .map(UserSettings::getGmailSearchQuery)
                .orElse(AppConstants.EMPTY_STRING);

        return Map.of(
                AppConstants.KEY_CONNECTED, isConnected,
                AppConstants.KEY_CLIENT_ID, resolvedClientId != null ? resolvedClientId : AppConstants.EMPTY_STRING,
                AppConstants.KEY_SEARCH_QUERY, searchQuery
        );
    }

    /**
     * Builds the Google OAuth2 authorization URL for the specified user.
     *
     * @param userId            internal user identifier
     * @param customRedirectUri optional override for OAuth redirect URI
     * @return full Google OAuth2 authorization URL
     */
    public String buildAuthUrl(String userId, String customRedirectUri) {
        String clientId = resolveClientId(userId);
        String redirect = resolveRedirectUri(customRedirectUri);

        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.ERR_CLIENT_ID_NOT_CONFIGURED);
        }

        String scope = resolveScope();
        String baseAuthUrl = resolveAuthBaseUrl();
        String separator = determineUrlSeparator(baseAuthUrl);

        return baseAuthUrl + separator +
                AppConstants.OAUTH_PARAM_CLIENT_ID + AppConstants.EQUALS + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                AMPERSAND + AppConstants.OAUTH_PARAM_REDIRECT_URI + AppConstants.EQUALS + URLEncoder.encode(redirect, StandardCharsets.UTF_8) +
                AMPERSAND + AppConstants.OAUTH_PARAM_RESPONSE_TYPE + AppConstants.EQUALS + AppConstants.OAUTH_VALUE_CODE +
                AMPERSAND + AppConstants.OAUTH_PARAM_SCOPE + AppConstants.EQUALS + URLEncoder.encode(scope, StandardCharsets.UTF_8) +
                AMPERSAND + AppConstants.OAUTH_PARAM_ACCESS_TYPE + AppConstants.EQUALS + AppConstants.OAUTH_VALUE_OFFLINE +
                AMPERSAND + AppConstants.OAUTH_PARAM_PROMPT + AppConstants.EQUALS + AppConstants.OAUTH_VALUE_CONSENT;
    }

    /**
     * Exchanges the authorization code for refresh and access tokens, and persists them to user settings.
     *
     * @param userId            internal user identifier
     * @param code              authorization code received from Google OAuth2 consent
     * @param customRedirectUri optional override for OAuth redirect URI
     * @return success map confirming Gmail connection
     */
    public Map<String, Object> exchangeCode(String userId, String code, String customRedirectUri) {
        String clientId = resolveClientId(userId);
        String clientSecret = resolveClientSecret(userId);
        String redirect = resolveRedirectUri(customRedirectUri);

        validateExchangeCredentials(code, clientId, clientSecret);

        try {
            String formBody = buildTokenRequestBody(code, clientId, clientSecret, redirect);
            String response = executeTokenRequest(formBody);

            JsonNode tokenData = objectMapper.readTree(response);
            saveTokenSettings(userId, tokenData);

            return Map.of(
                    AppConstants.KEY_SUCCESS, true,
                    AppConstants.KEY_MESSAGE, AppConstants.MSG_GMAIL_CONNECTED
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token exchange failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.ERR_TOKEN_EXCHANGE_FAILED_PREFIX + e.getMessage());
        }
    }

    private void validateExchangeCredentials(String code, String clientId, String clientSecret) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.ERR_AUTH_CODE_REQUIRED);
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AppConstants.ERR_OAUTH_CREDENTIALS_NOT_CONFIGURED);
        }
    }

    private String buildTokenRequestBody(String code, String clientId, String clientSecret, String redirect) {
        Map<String, String> formParams = Map.of(
                AppConstants.OAUTH_PARAM_CODE, code,
                AppConstants.OAUTH_PARAM_CLIENT_ID, clientId,
                AppConstants.OAUTH_PARAM_CLIENT_SECRET, clientSecret,
                AppConstants.OAUTH_PARAM_REDIRECT_URI, redirect,
                AppConstants.OAUTH_PARAM_GRANT_TYPE, AppConstants.OAUTH_VALUE_AUTHORIZATION_CODE
        );
        return buildFormBody(formParams);
    }

    private String executeTokenRequest(String formBody) {
        String tokenBaseUrl = resolveTokenBaseUrl();
        RestClient restClient = RestClient.builder().baseUrl(tokenBaseUrl).build();

        return restClient.post()
                .uri(AppConstants.OAUTH_TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(String.class);
    }

    private void saveTokenSettings(String userId, JsonNode tokenData) {
        String refreshToken = tokenData.path(AppConstants.OAUTH_FIELD_REFRESH_TOKEN).asText(null);
        String accessToken = tokenData.path(AppConstants.OAUTH_FIELD_ACCESS_TOKEN).asText(null);
        long defaultExpiry = resolveTokenExpirySeconds();
        long expiresIn = tokenData.path(AppConstants.OAUTH_FIELD_EXPIRES_IN).asLong(defaultExpiry);

        UserSettings settings = settingsService.getOrCreateSettings(userId);
        if (refreshToken != null && !refreshToken.isBlank()) {
            settings.setGmailRefreshToken(refreshToken);
        }
        if (accessToken != null) {
            settings.setGmailAccessToken(accessToken);
            settings.setTokenExpiresAt(System.currentTimeMillis() + (expiresIn * SecurityConstants.MILLIS_IN_SECOND));
        }
        settings.setGmailConnected(true);
        settings.setUpdatedAt(Instant.now());
        userSettingsRepository.save(settings);
    }

    private String resolveRedirectUri(String customRedirectUri) {
        if (customRedirectUri != null && !customRedirectUri.isBlank()) {
            return customRedirectUri;
        }
        return gmailConfig.getRedirectUri();
    }

    private String resolveScope() {
        if (gmailConfig.getScope() != null && !gmailConfig.getScope().isBlank()) {
            return gmailConfig.getScope().trim();
        }
        return AppConstants.DEFAULT_GMAIL_SCOPE;
    }

    private String resolveAuthBaseUrl() {
        if (gmailConfig.getAuthUrl() != null && !gmailConfig.getAuthUrl().isBlank()) {
            return gmailConfig.getAuthUrl().trim();
        }
        return AppConstants.DEFAULT_GMAIL_AUTH_URL;
    }

    private String resolveTokenBaseUrl() {
        if (gmailConfig.getTokenUrl() != null && !gmailConfig.getTokenUrl().isBlank()) {
            return gmailConfig.getTokenUrl().trim();
        }
        return AppConstants.DEFAULT_GMAIL_TOKEN_URL;
    }

    private String determineUrlSeparator(String url) {
        if (!url.contains(AppConstants.QUESTION_MARK)) {
            return AppConstants.QUESTION_MARK;
        }
        if (url.endsWith(AppConstants.QUESTION_MARK)) {
            return AppConstants.EMPTY_STRING;
        }
        return AMPERSAND;
    }

    private long resolveTokenExpirySeconds() {
        if (gmailConfig.getTokenExpirySeconds() > 0) {
            return gmailConfig.getTokenExpirySeconds();
        }
        return AppConstants.DEFAULT_GMAIL_TOKEN_EXPIRY_SECONDS;
    }

    /**
     * Checks if Gmail API credentials are fully configured and valid.
     */
    public boolean isConfigured(String userId) {
        String clientId = resolveClientId(userId);
        String clientSecret = resolveClientSecret(userId);
        String refreshToken = resolveRefreshToken(userId);
        return clientId != null && !clientId.isBlank() &&
                clientSecret != null && !clientSecret.isBlank() &&
                refreshToken != null && !refreshToken.isBlank();
    }

    /**
     * Creates an authenticated Gmail API client using OAuth2 refresh token.
     */
    public Gmail getGmailClient(String userId) throws GeneralSecurityException, IOException {
        String clientId = resolveClientId(userId);
        String clientSecret = resolveClientSecret(userId);
        String refreshToken = resolveRefreshToken(userId);

        if (clientId == null || clientSecret == null || refreshToken == null) {
            throw new IllegalStateException("Gmail OAuth credentials (Client ID, Client Secret, Refresh Token) are not configured.");
        }

        String accessToken = refreshAccessToken(clientId, clientSecret, refreshToken);

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Gmail.Builder(httpTransport, GsonFactory.getDefaultInstance(), requestInitializer)
                .setApplicationName("Caliber")
                .build();
    }

    /**
     * Refreshes access token via Google OAuth2 token endpoint.
     */
    public String refreshAccessToken(String clientId, String clientSecret, String refreshToken) {
        try {
            Map<String, String> params = Map.of(
                    AppConstants.OAUTH_PARAM_CLIENT_ID, clientId,
                    AppConstants.OAUTH_PARAM_CLIENT_SECRET, clientSecret,
                    AppConstants.OAUTH_PARAM_REFRESH_TOKEN, refreshToken,
                    AppConstants.OAUTH_PARAM_GRANT_TYPE, AppConstants.OAUTH_VALUE_REFRESH_TOKEN
            );

            String response = executeTokenRequest(buildFormBody(params));

            JsonNode node = objectMapper.readTree(response);
            return node.path(AppConstants.OAUTH_FIELD_ACCESS_TOKEN).asText();
        } catch (Exception e) {
            log.error("Failed to refresh Gmail access token: {}", e.getMessage());
            throw new OAuthTokenException(AppConstants.ERR_TOKEN_REFRESH_FAILED_PREFIX + e.getMessage(), e);
        }
    }

    public String resolveClientId(String userId) {
        if (userId != null) {
            Optional<UserSettings> s = userSettingsRepository.findByUserId(userId);
            if (s.isPresent() && s.get().getGmailClientId() != null && !s.get().getGmailClientId().isBlank()) {
                return s.get().getGmailClientId();
            }
        }
        return gmailConfig.getClientId();
    }

    public String resolveClientSecret(String userId) {
        if (userId != null) {
            Optional<UserSettings> s = userSettingsRepository.findByUserId(userId);
            if (s.isPresent() && s.get().getGmailClientSecret() != null && !s.get().getGmailClientSecret().isBlank()) {
                return s.get().getGmailClientSecret();
            }
        }
        return gmailConfig.getClientSecret();
    }

    public String resolveRefreshToken(String userId) {
        if (userId != null) {
            Optional<UserSettings> s = userSettingsRepository.findByUserId(userId);
            if (s.isPresent() && s.get().getGmailRefreshToken() != null && !s.get().getGmailRefreshToken().isBlank()) {
                return s.get().getGmailRefreshToken();
            }
        }
        return gmailConfig.getRefreshToken();
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(AppConstants.AMPERSAND);
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append(AppConstants.EQUALS);
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
