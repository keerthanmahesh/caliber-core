package com.caliber.service;

import com.caliber.model.AuthProvider;
import com.caliber.model.User;
import com.caliber.repository.UserRepository;
import com.security.data.dto.AppJwtClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOrCreateCurrentUser_WhenAuthenticated_ReturnsExistingUser() {
        AppJwtClaims claims = AppJwtClaims.builder()
                .userId("100839277841197849047")
                .email("test@example.com")
                .name("Test User")
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        User mockUser = User.builder()
                .id("66edb5c84a8b29103e91a721")
                .sub("100839277841197849047")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();

        when(userRepository.findBySub("100839277841197849047")).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.getOrCreateCurrentUser();

        assertNotNull(result);
        assertEquals("66edb5c84a8b29103e91a721", result.getId());
        assertEquals("100839277841197849047", result.getSub());
        assertEquals("test@example.com", result.getEmail());
        verify(userRepository, times(1)).findBySub("100839277841197849047");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void getOrCreateCurrentUser_WhenNotFoundBySub_FallsBackToEmail() {
        AppJwtClaims claims = AppJwtClaims.builder()
                .userId("100839277841197849047")
                .email("test@example.com")
                .name("Test User")
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        User mockUser = User.builder()
                .id("66edb5c84a8b29103e91a722")
                .email("test@example.com")
                .build();

        when(userRepository.findBySub("100839277841197849047")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.getOrCreateCurrentUser();

        assertNotNull(result);
        assertEquals("66edb5c84a8b29103e91a722", result.getId());
        assertEquals("100839277841197849047", result.getSub());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void getOrCreateCurrentUser_WhenNewUser_AutoRegistersAndSetsSub() {
        AppJwtClaims claims = AppJwtClaims.builder()
                .userId("100839277841197849047")
                .email("newuser@example.com")
                .name("John Doe")
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findBySub("100839277841197849047")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("66edb5c84a8b29103e91a723");
            return u;
        });

        User result = userService.getOrCreateCurrentUser();

        assertNotNull(result);
        assertEquals("66edb5c84a8b29103e91a723", result.getId());
        assertEquals("100839277841197849047", result.getSub());
        assertEquals("newuser@example.com", result.getEmail());
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals(AuthProvider.GOOGLE, result.getAuthProvider());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void resolveCurrentUserId_ReturnsInternalMongoObjectId() {
        AppJwtClaims claims = AppJwtClaims.builder()
                .userId("100839277841197849047")
                .email("test@example.com")
                .name("Test User")
                .build();

        var auth = new UsernamePasswordAuthenticationToken(claims, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        User mockUser = User.builder()
                .id("66edb5c84a8b29103e91a721")
                .sub("100839277841197849047")
                .email("test@example.com")
                .build();

        when(userRepository.findBySub("100839277841197849047")).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String resolvedId = userService.resolveCurrentUserId();

        assertEquals("66edb5c84a8b29103e91a721", resolvedId);
    }

    @Test
    void getOrCreateCurrentUser_WhenUnauthenticated_Throws401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.getOrCreateCurrentUser());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void getOrCreateCurrentUser_WhenClaimsNullAndUserNotFound_Throws404() {
        var auth = new UsernamePasswordAuthenticationToken("customPrincipal", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findBySub(anyString())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.getOrCreateCurrentUser());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
