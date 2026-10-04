package com.caliber.service;

import com.caliber.constant.AppConstants;
import com.caliber.model.AuthProvider;
import com.caliber.model.User;
import com.caliber.repository.UserRepository;
import com.security.core.util.SecurityUtil;
import com.security.data.dto.AppJwtClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

/**
 * Service managing user identity resolution, ObjectId lookup, and Just-In-Time (JIT) registration in Caliber.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Resolves the current authenticated user's internal MongoDB ObjectId string.
     * Guaranteed to return a valid MongoDB ObjectId after ensuring the user is provisioned.
     *
     * @return 24-character hexadecimal MongoDB ObjectId string
     */
    public String resolveCurrentUserId() {
        return getOrCreateCurrentUser().getId();
    }

    /**
     * Resolves the current authenticated user by Google sub or email, updating lastLoginAt if present,
     * or auto-provisioning a new user record in MongoDB on first access.
     *
     * @return persisted User document with auto-generated MongoDB ObjectId
     * @throws ResponseStatusException if unauthenticated (401) or claims cannot be resolved (404)
     */
    public User getOrCreateCurrentUser() {
        String sub = SecurityUtil.getAuthenticatedUserId();
        if (isUnauthenticated(sub)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        AppJwtClaims claims = extractAppJwtClaims();
        String email = claims != null ? claims.email() : null;

        return findExistingUser(sub, email)
                .map(existingUser -> updateExistingUser(existingUser, sub))
                .orElseGet(() -> provisionNewUser(sub, email, claims));
    }

    private boolean isUnauthenticated(String sub) {
        return sub == null || sub.isBlank() || AppConstants.ANONYMOUS_USER.equalsIgnoreCase(sub);
    }

    private AppJwtClaims extractAppJwtClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppJwtClaims claims) {
            return claims;
        }
        return null;
    }

    private Optional<User> findExistingUser(String sub, String email) {
        Optional<User> userBySub = userRepository.findBySub(sub);
        if (userBySub.isPresent()) {
            return userBySub;
        }
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email);
        }
        return Optional.empty();
    }

    private User updateExistingUser(User existingUser, String sub) {
        if (existingUser.getSub() == null || existingUser.getSub().isBlank()) {
            existingUser.setSub(sub);
        }
        existingUser.setLastLoginAt(Instant.now());
        existingUser.setLastUpdatedTime(System.currentTimeMillis());
        return userRepository.save(existingUser);
    }

    private User provisionNewUser(String sub, String email, AppJwtClaims claims) {
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile cannot be resolved");
        }
        log.info("Auto-registering user in Caliber: sub={}, email={}", sub, email);
        String[] nameParts = parseName(claims.name());
        long now = System.currentTimeMillis();
        String identifier = (email != null && !email.isBlank()) ? email : sub;

        // Note: id is omitted so MongoDB generates a native ObjectId
        User newUser = User.builder()
                .sub(sub)
                .email(email)
                .username(identifier)
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .authProvider(AuthProvider.GOOGLE)
                .lastLoginAt(Instant.now())
                .createdTime(now)
                .lastUpdatedTime(now)
                .createdBy(identifier)
                .lastUpdatedBy(identifier)
                .build();

        return userRepository.save(newUser);
    }

    private String[] parseName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{AppConstants.DEFAULT_USER_FIRST_NAME, AppConstants.EMPTY_STRING};
        }
        String[] parts = fullName.trim().split("\\s+", 2);
        return new String[]{
                parts[0],
                parts.length > 1 ? parts[1] : AppConstants.EMPTY_STRING
        };
    }
}
