package com.example.api_gateway.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * ==========================================
 * CORS CONFIGURATION
 * ==========================================
 * Konfigurerar Cross-Origin Resource Sharing (CORS) för API Gateway.
 * 
 * Detta är nödvändigt för att tillåta frontend (React app) att kommunicera
 * med backend API:et från en annan origin/port.
 * 
 * SÄKERHET:
 * - Endast specifika origins tillåts (inte *)
 * - Credentials (JWT tokens) tillåts för autentisering
 * - Alla HTTP-metoder tillåts (GET, POST, PUT, DELETE, etc.)
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // ALLOWED ORIGINS - lägg till fler vid behov
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", // Vite dev server (standard)
                "http://localhost:3000", // Alternative React dev server
                "http://localhost:4173" // Vite preview server
        ));

        // ALLOWED METHODS - tillåt alla HTTP-metoder
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("OPTIONS");

        // ALLOWED HEADERS - tillåt alla headers (inkl. Authorization för JWT)
        config.addAllowedHeader("*");

        // EXPOSED HEADERS - Headers som frontend får läsa från response
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Type");

        // ALLOW CREDENTIALS - Nödvändigt för att skicka JWT tokens
        config.setAllowCredentials(true);

        // MAX AGE - Hur länge browsers får cacha preflight requests (OPTIONS)
        config.setMaxAge(3600L); // 1 timme

        // Applicera CORS config på alla endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
