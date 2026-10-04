package com.caliber.config;

import com.security.core.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(jwtAuthenticationFilter);
        ReflectionTestUtils.setField(securityConfig, "allowedOrigins", List.of("http://localhost:3000", "https://caliber-ui.vercel.app"));
        ReflectionTestUtils.setField(securityConfig, "allowedMethods", List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        ReflectionTestUtils.setField(securityConfig, "allowedHeaders", List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "Cookie"));
    }

    @Test
    void corsConfigurationSource_ConfiguresOriginsHeadersAndMethods() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertTrue(config.getAllowCredentials());
        assertEquals(3600L, config.getMaxAge());
        assertEquals(List.of("http://localhost:3000", "https://caliber-ui.vercel.app"), config.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"), config.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "Cookie"), config.getAllowedHeaders());
    }

    @Test
    void corsConfigurationSource_AppliesToAnyPath() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertEquals(List.of("http://localhost:3000", "https://caliber-ui.vercel.app"), config.getAllowedOrigins());
    }
}
