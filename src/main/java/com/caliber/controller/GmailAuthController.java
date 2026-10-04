package com.caliber.controller;

import com.caliber.constant.AppConstants;
import com.caliber.service.GmailAuthService;
import com.caliber.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for Gmail OAuth2 authentication, authorization URL generation,
 * and token exchange.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gmail")
@RequiredArgsConstructor
public class GmailAuthController {

    private final GmailAuthService gmailAuthService;
    private final UserService userService;

    private String resolveUserId() {
        return userService.resolveCurrentUserId();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String userId = resolveUserId();
        return ResponseEntity.ok(gmailAuthService.getStatus(userId));
    }

    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl(
            @RequestParam(value = AppConstants.KEY_REDIRECT_URI, required = false) String customRedirectUri
    ) {
        String userId = resolveUserId();
        String authUrl = gmailAuthService.buildAuthUrl(userId, customRedirectUri);
        return ResponseEntity.ok(Map.of(AppConstants.KEY_AUTH_URL, authUrl));
    }

    @PostMapping("/exchange")
    public ResponseEntity<Map<String, Object>> exchangeCode(@RequestBody Map<String, String> payload) {
        String userId = resolveUserId();
        String code = payload.get(AppConstants.KEY_CODE);
        String customRedirectUri = payload.get(AppConstants.KEY_REDIRECT_URI);
        return ResponseEntity.ok(gmailAuthService.exchangeCode(userId, code, customRedirectUri));
    }
}
