package com.enterprise.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Set FRONTEND_ORIGIN env var to your deployed frontend URL (e.g.
    // https://enterprise-platform-frontend.onrender.com). Defaults to "*" for
    // local development only — tighten this before treating the deployment as
    // anything beyond a demo.
    @Value("${app.frontend-origin:*}")
    private String frontendOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
