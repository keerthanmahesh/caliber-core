package com.caliber.config;

import com.caliber.constant.AppConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "caliber.ai")
public class AiConfig {

    private int maxBodyChars = AppConstants.DEFAULT_MAX_BODY_CHARS;
    private OllamaProperties ollama = new OllamaProperties();
    private GeminiProperties gemini = new GeminiProperties();

    @Data
    public static class OllamaProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        private int timeoutSeconds;
    }

    @Data
    public static class GeminiProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        private int timeoutSeconds;
    }
}
