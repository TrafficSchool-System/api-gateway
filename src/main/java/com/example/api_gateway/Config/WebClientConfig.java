package com.example.api_gateway.Config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ==========================================
 * WEBCLIENT CONFIGURATION
 * ==========================================
 * 
 * Konfigurerar WebClient med Eureka service discovery.
 * Används för service-to-service kommunikation från Gateway.
 * 
 * @LoadBalanced:
 *                - Möjliggör användning av service-namn istället för URL:er
 *                - Exempel: "http://USER-SERVICE/api/users/1" istället för
 *                "http://localhost:8081/api/users/1"
 *                - Automatisk load balancing om flera instanser körs
 * 
 *                ANVÄNDS AV:
 *                - SubscriptionValidationFilter (för att kolla subscription i
 *                UserService)
 */
@Configuration
public class WebClientConfig {

    /**
     * WebClient med Eureka service discovery och load balancing
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
