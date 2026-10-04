package com.caliber.controller;

import com.caliber.dto.AiTestResponse;
import com.caliber.dto.UserSettingsDto;
import com.caliber.service.SettingsService;
import com.caliber.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for retrieving and updating user settings, templates, and testing AI extraction.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final UserService userService;

    private String resolveUserId() {
        return userService.resolveCurrentUserId();
    }

    @GetMapping
    public ResponseEntity<UserSettingsDto> getSettings() {
        return ResponseEntity.ok(settingsService.getSettingsDto(resolveUserId()));
    }

    @PutMapping
    public ResponseEntity<UserSettingsDto> updateSettings(@RequestBody UserSettingsDto dto) {
        return ResponseEntity.ok(settingsService.updateSettings(resolveUserId(), dto));
    }

    @PostMapping("/test-ai")
    public ResponseEntity<AiTestResponse> testAi() {
        return ResponseEntity.ok(settingsService.testAi(resolveUserId()));
    }
}
