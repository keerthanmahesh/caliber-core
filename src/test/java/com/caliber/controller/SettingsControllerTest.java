package com.caliber.controller;

import com.caliber.dto.AiExtractedJob;
import com.caliber.dto.AiTestResponse;
import com.caliber.dto.UserSettingsDto;
import com.caliber.service.SettingsService;
import com.caliber.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private UserService userService;

    @InjectMocks
    private SettingsController settingsController;

    @Test
    void getSettings_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        UserSettingsDto dto = UserSettingsDto.builder().userId("user-1").build();
        when(settingsService.getSettingsDto("user-1")).thenReturn(dto);

        ResponseEntity<UserSettingsDto> response = settingsController.getSettings();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("user-1", response.getBody().getUserId());
        verify(settingsService).getSettingsDto("user-1");
    }

    @Test
    void updateSettings_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        UserSettingsDto dto = UserSettingsDto.builder().userId("user-1").gmailSearchQuery("test").build();
        when(settingsService.updateSettings("user-1", dto)).thenReturn(dto);

        ResponseEntity<UserSettingsDto> response = settingsController.updateSettings(dto);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("test", response.getBody().getGmailSearchQuery());
        verify(settingsService).updateSettings("user-1", dto);
    }

    @Test
    void testAi_ResolvesUserIdAndDelegatesToService() {
        when(userService.resolveCurrentUserId()).thenReturn("user-1");
        AiExtractedJob mockJob = AiExtractedJob.builder().jobTitle("Senior Dev").build();
        AiTestResponse mockResponse = AiTestResponse.success("OK", mockJob);
        when(settingsService.testAi("user-1")).thenReturn(mockResponse);

        ResponseEntity<AiTestResponse> response = settingsController.testAi();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("OK", response.getBody().message());
        assertEquals(mockJob, response.getBody().data());
        verify(settingsService).testAi("user-1");
    }
}
