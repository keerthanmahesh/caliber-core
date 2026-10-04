package com.caliber.service;

import com.caliber.config.GmailConfig;
import com.caliber.constant.AppConstants;
import com.caliber.exception.OAuthTokenException;
import com.caliber.model.UserSettings;
import com.caliber.repository.UserSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailAuthServiceTest {

    @Mock
    private GmailConfig gmailConfig;

    @Mock
    private SettingsService settingsService;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GmailAuthService gmailAuthService;

    @BeforeEach
    void setUp() {
        gmailAuthService = new GmailAuthService(
                gmailConfig,
                settingsService,
                userSettingsRepository,
                objectMapper
        );
    }

    @Test
    void getStatus_ReturnsExpectedStatusMap() {
        String userId = "usr_123";
        UserSettings mockSettings = UserSettings.builder()
                .userId(userId)
                .gmailClientId("mock-client-id")
                .gmailClientSecret("mock-secret")
                .gmailRefreshToken("mock-refresh-token")
                .gmailSearchQuery("label:inbox")
                .build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(mockSettings));

        Map<String, Object> status = gmailAuthService.getStatus(userId);

        assertNotNull(status);
        assertEquals(true, status.get(AppConstants.KEY_CONNECTED));
        assertEquals("mock-client-id", status.get(AppConstants.KEY_CLIENT_ID));
        assertEquals("label:inbox", status.get(AppConstants.KEY_SEARCH_QUERY));
    }

    @Test
    void buildAuthUrl_ThrowsBadRequest_WhenClientIdMissing() {
        String userId = "usr_123";
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(gmailConfig.getClientId()).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                gmailAuthService.buildAuthUrl(userId, null));

        assertTrue(ex.getReason().contains("Gmail Client ID is not configured"));
    }

    @Test
    void buildAuthUrl_ConstructsValidOAuthUrl() {
        String userId = "usr_123";
        UserSettings mockSettings = UserSettings.builder()
                .userId(userId)
                .gmailClientId("test-client-id.apps.googleusercontent.com")
                .build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(mockSettings));
        when(gmailConfig.getRedirectUri()).thenReturn("http://localhost:3000/settings");
        when(gmailConfig.getScope()).thenReturn(AppConstants.DEFAULT_GMAIL_SCOPE);
        when(gmailConfig.getAuthUrl()).thenReturn(AppConstants.DEFAULT_GMAIL_AUTH_URL);

        String authUrl = gmailAuthService.buildAuthUrl(userId, null);

        assertNotNull(authUrl);
        assertTrue(authUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        assertTrue(authUrl.contains("client_id=test-client-id.apps.googleusercontent.com"));
        assertTrue(authUrl.contains("redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fsettings"));
        assertTrue(authUrl.contains("response_type=code"));
        assertTrue(authUrl.contains("access_type=offline"));
        assertTrue(authUrl.contains("prompt=consent"));
    }

    @Test
    void exchangeCode_ThrowsBadRequest_WhenCodeIsMissing() {
        String userId = "usr_123";
        UserSettings mockSettings = UserSettings.builder()
                .userId(userId)
                .gmailClientId("mock-id")
                .gmailClientSecret("mock-secret")
                .build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(mockSettings));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                gmailAuthService.exchangeCode(userId, "", null));

        assertTrue(ex.getReason().contains("Authorization code is required"));
    }

    @Test
    void exchangeCode_ThrowsBadRequest_WhenCredentialsMissing() {
        String userId = "usr_123";
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(gmailConfig.getClientId()).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                gmailAuthService.exchangeCode(userId, "sample-auth-code", null));

        assertTrue(ex.getReason().contains("Gmail OAuth credentials"));
    }

    @Test
    void isConfigured_ReturnsFalse_WhenRefreshTokenMissing() {
        String userId = "usr_123";
        UserSettings mockSettings = UserSettings.builder()
                .userId(userId)
                .gmailClientId("mock-id")
                .gmailClientSecret("mock-secret")
                .gmailRefreshToken("")
                .build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(mockSettings));
        when(gmailConfig.getRefreshToken()).thenReturn(null);

        assertFalse(gmailAuthService.isConfigured(userId));
    }

    @Test
    void isConfigured_ReturnsTrue_WhenAllCredentialsPresent() {
        String userId = "usr_123";
        UserSettings mockSettings = UserSettings.builder()
                .userId(userId)
                .gmailClientId("mock-id")
                .gmailClientSecret("mock-secret")
                .gmailRefreshToken("mock-token")
                .build();
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(mockSettings));

        assertTrue(gmailAuthService.isConfigured(userId));
    }

    @Test
    void refreshAccessToken_ThrowsOAuthTokenException_OnFailure() {
        when(gmailConfig.getTokenUrl()).thenReturn("http://invalid-token-url-that-fails.example");

        OAuthTokenException ex = assertThrows(OAuthTokenException.class, () ->
                gmailAuthService.refreshAccessToken("client-1", "secret-1", "refresh-1"));

        assertTrue(ex.getMessage().contains(AppConstants.ERR_TOKEN_REFRESH_FAILED_PREFIX));
    }
}
