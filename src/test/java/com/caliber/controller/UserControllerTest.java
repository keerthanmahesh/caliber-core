package com.caliber.controller;

import com.caliber.model.User;
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
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void getCurrentUser_ReturnsUserFromService() {
        User mockUser = User.builder()
                .id("usr_123")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();

        when(userService.getOrCreateCurrentUser()).thenReturn(mockUser);

        ResponseEntity<User> response = userController.getCurrentUser();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("test@example.com", response.getBody().getEmail());
        verify(userService, times(1)).getOrCreateCurrentUser();
    }
}
